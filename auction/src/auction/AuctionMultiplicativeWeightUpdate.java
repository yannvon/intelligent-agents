package auction;

import static helpers.AuctionHelper.cumulativePoissonDistribution;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import experts.*;
import helpers.ActionEntry;
import helpers.AuctionHelper;
import helpers.CentralizedPlanning;
import logist.LogistSettings;

//the list of imports
import logist.agent.Agent;
import logist.behavior.AuctionBehavior;
import logist.config.Parsers;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;


/**
 * A agent that behaves in two phases:
 * 1) Compute estimated savings if winning task t, and bidding at deficit to be more competitive in future
 * 2) As we don't know our adversaries behavior and implemented many agents that we call experts, we use the
 * multiplicative weighted update method to decide on which expert to trust. Each expert chooses a bid
 * using the estimated marginal cost and possible an estimate of the opponents marginal cost too.
 * <p>
 * The underlying algorithm is stochastic local search with some additional improvements and tweaks.
 */
@SuppressWarnings("unused")
public class AuctionMultiplicativeWeightUpdate implements AuctionBehavior {

    private static final boolean VERBOSE = false;
    private static final boolean SHUFFLE = false;

    private static final double STARTING_RATIO = 0.5;
    private static final double STARTING_SECURE_FACTOR = 0.75;

    private static final double TAX = 2;

    private static final int PHASE1_END = 5;
    private static final int N_EXPECTED_TASK = 5;
    private static final double PHASE_1_SAVINGS_FACTOR = 0.2;

    private Topology topology;
    private TaskDistribution distribution;
    private Agent agent;
    private Random random;
    private List<Vehicle> vehicles;
    private int maxVehicleCapacity;

    private ActionEntry[] currentSolution;
    private double currentCost;

    private double potentialCost;
    private ActionEntry[] potentialSolution;

    private ActionEntry[] currentOpponentSolution;
    private double currentOpponentCost;

    private double potentialOpponentCost;
    private ActionEntry[] potentialOpponentSolution;

    private int currentExpert;
    private Expert[] experts;
    private Long[] expertsBids;

    private CentralizedPlanning centralizedPlanning;
    private long timeout_bid;
    private long timeout_plan;

    private double marginalCost;

    private double[] weights;

    private int nAuctions;
    private HashMap<Task, Double> taskProbabilities;

    @Override
    public void setup(Topology topology, TaskDistribution distribution, Agent agent) {
        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config" + File.separator + "settings_auction.xml");
        } catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }

        // Get timeout values
        timeout_bid = ls.get(LogistSettings.TimeoutKey.BID);
        timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);

        // Set basic attributes
        this.topology = topology;
        this.distribution = distribution;
        this.agent = agent;
        this.vehicles = agent.vehicles();
        this.maxVehicleCapacity = Integer.MIN_VALUE;
        for (Vehicle v : vehicles) {
            this.maxVehicleCapacity = (v.capacity() > this.maxVehicleCapacity) ? v.capacity() : this.maxVehicleCapacity;
        }

        long seed = -9019554669489983951L * this.hashCode() * agent.id();
        this.random = new Random(seed);

        // Initialize Action entries
        this.currentSolution = new ActionEntry[agent.vehicles().size()];
        for (int i = 0; i < agent.vehicles().size(); i++) {
            currentSolution[i] = new ActionEntry(i);
        }

        this.currentOpponentSolution = new ActionEntry[agent.vehicles().size()];
        for (int i = 0; i < agent.vehicles().size(); i++) {
            currentOpponentSolution[i] = new ActionEntry(i);
        }

        // Initialize centralized planning
        centralizedPlanning = new CentralizedPlanning();
        centralizedPlanning.setup(this.distribution, this.agent);

        // Initializations for Phase 1
        this.nAuctions = 0;
        this.taskProbabilities = new HashMap<>();

        for (City c1 : topology.cities()) {
            for (City c2 : topology.cities()) {
                Task t = new Task(0, c1, c2, 0, distribution.weight(c1, c2));
                this.taskProbabilities.put(t, distribution.probability(c1, c2));
            }
        }

        // Initializations for Phase 2
        this.marginalCost = 0;
        this.currentExpert = 0;

        // --- IMPORTANT : Choose all experts that we think are the best performing ---
        this.experts = new Expert[]{new MaxMarginal(),
                                    new Ratio(1, TAX, 1),
                                    new RatioCustom(1, TAX, 1, (x, y) -> y ? x * 1.1 : x * 0.8),
                                    new Adaptive(1, 0.8, 0.9, TAX)};

        this.expertsBids = new Long[experts.length];
        this.weights = new double[experts.length];
        for (int i = 0; i < experts.length; i++) {
            weights[i] = 1.0 / experts.length;
        }
    }

    @Override
    public void auctionResult(Task previous, int winner, Long[] bids) {
        nAuctions++;

        boolean won = winner == agent.id();

        if (won) {
            currentSolution = potentialSolution;
            currentCost = potentialCost;

            potentialSolution = null;
            potentialCost = -1;
        } else {
            currentOpponentSolution = potentialOpponentSolution;
            currentOpponentCost = potentialOpponentCost;

            potentialOpponentSolution = null;
            potentialOpponentCost = -1;
        }

        // Multiplicative weighted update - only after phase 1 is over
        if (nAuctions > PHASE1_END) {

            Long opBid;
            // FIXME maybe find winning value instead ?
            if (agent.id() == 0 && bids.length > 1) {   // FIXME test with only 1 agent and with 3 agents
                opBid = bids[1];
            } else {
                opBid = bids[0];
            }
            double sumW = 0.0;
            for (int eId = 0; eId < experts.length; eId++) {
                boolean expertWin = opBid == null || opBid > expertsBids[eId];
                experts[eId].update(expertWin, opBid);  // FIXME what does this do ?

                double reward = expertWin ? expertsBids[eId] - marginalCost : 0.0;
                double maxReward = (opBid != null) ? opBid - marginalCost : 100000; // FIXME very large value as we could have bid a lot here
                double multiplicativeFactor;
                if (opBid == null || opBid < 0L) {
                    multiplicativeFactor = reward > 0.0 ? reward : 1;
                } else if (reward < 0 || maxReward < 0) {    // FIXME crucial part of algorithm
                    multiplicativeFactor = 1.0;
                } else {
                    multiplicativeFactor = 1.0 + reward / (maxReward);
                }
                weights[eId] *= multiplicativeFactor;
                sumW += weights[eId];
            }
            // Normalize weights and find best performing expert
            int max = 0;
            for (int eId = 0; eId < experts.length; eId++) {
                weights[eId] = weights[eId] / sumW;
                if (weights[max] < weights[eId]) {
                    max = eId;
                }
            }
            currentExpert = max;
        }
    }

    @Override
    public Long askPrice(Task task) {

        if (VERBOSE) {
            System.out.println();
            System.out.println("AGENT " + agent.id() + "--- TASK " + task.id + "---");
        }

        /*
         * NOTE: We have no choice but returning null when we can't carry a task. In an unfair setting where the
         * adversary is able to carry it he could conclude that we don't have enough max capacity and bid exorbitantly
         * high for a following task with high weight. However we could also be faking having low capacity by
         * deliberatively sending null. In that case we could trick the opponent and reap big rewards. This can go on
         * until both are back to bidding meaningful values.
         */
        if (this.maxVehicleCapacity < task.weight)
            return null;


        /*
         * BASIC MECHANISM: Find own marginal cost
         *
         * Improve SLS algorithm by using previous solution and adding task at best possible location and starting
         * SLS from there. (If SHUFFLE = true)
         * We iterate over all vehicles and all possible slots.
         */
        double costWithNewTask = addingTaskCost(task);
        if (SHUFFLE) {
            centralizedPlanning.shuffle(vehicles, potentialSolution, timeout_bid / 2);
            costWithNewTask = computeCost(potentialSolution, vehicles);
        }
        marginalCost = costWithNewTask - currentCost;

        /*
         * ADDITIONAL MECHANISM : Estimate opponent marginal cost
         *
         * Using the (wrong) assumption that the adversary starts at the same city as our own agent, we try
         * to estimate the opponents marginal cost.
         */
        double opponentCost = addingTaskCost(task, true);
        if (SHUFFLE) {
            centralizedPlanning.shuffle(vehicles, potentialOpponentSolution, timeout_bid / 2);
            opponentCost = computeCost(potentialOpponentSolution, vehicles);
        }
        double marginalOpponentCost = opponentCost - currentOpponentCost;

        long bid = Math.round(marginalCost);

        if (nAuctions < PHASE1_END) {
            /*
             * --- PHASE 1 ---
             *
             * Find conservative estimate of savings on the future marginal cost when taking task t.
             */

            double savings = savings(task);
            bid -= PHASE_1_SAVINGS_FACTOR * savings;

            // System.out.println("Savings: " + savings);


        } else {
            /*
             * --- PHASE 2 ---
             *
             * Use multiplicative weight update method to choose an expert advice.
             */

            // Compute all bids
            long weightedBid = 0;
            for (int eId = 0; eId < experts.length; eId++) {
                expertsBids[eId] = experts[eId].bid(marginalCost, marginalOpponentCost);
                weightedBid += expertsBids[eId] * weights[eId];
            }

            // Here we can choose between trusting the current best performing expert or the weighted average
            // among all experts.
            // bid = expertsBids[currentExpert];
            bid = weightedBid;
        }

        if (VERBOSE) {
            System.out.println("Current cost: " + currentCost);
            System.out.println("Cost with potential Task:" + costWithNewTask);
            System.out.println("Marginal cost of adding Task: " + marginalCost);
            System.out.println();
            System.out.println("sim op cost: " + currentOpponentCost);
            System.out.println("sim op Cost with potential Task:" + opponentCost);
            System.out.println("sim op Marginal cost: " + marginalOpponentCost);

            System.out.println("\nBid by " + experts[currentExpert].name() + " :" + bid);
        }

        return bid;
    }

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {

        List<Plan> plans = planCentralized(tasks);

        AuctionHelper.displayPerformance(getClass().toString(), tasks, plans, vehicles);

        return plans;
    }

    /**
     * Use the centralized planner starting from our current best solution to try and find a better schedule.
     *
     * @param tasks tasks that were won at auction
     * @return a plan for each vehicle
     */
    private List<Plan> planCentralized(TaskSet tasks) {

        // Plan
        ActionEntry[] bestSolution =
                centralizedPlanning.shuffle(this.vehicles, currentSolution, Math.round(timeout_plan * 0.9));    // FIXME secure factor

        // Get plan from new task set
        List<Plan> plans = centralizedPlanning.planFromSolutionAndTaskSet(bestSolution, this.vehicles, tasks);

        return plans;
    }

    /**
     * Compute cost of adding a task to current schedule.
     *
     * @param t task
     * @return cost
     */
    private double addingTaskCost(Task t) {
        return addingTaskCost(t, false);
    }

    /**
     * Compute cost of adding a task to current schedule.
     *
     * @param t task
     * @param opponent boolean indicating whether we are simulating opponent or not
     * @return cost
     */
    private double addingTaskCost(Task t, boolean opponent) {

        double lowestTotalCostFound = Double.MAX_VALUE;
        ActionEntry[] bestPlan = null;
        ActionEntry[] current = opponent ? currentOpponentSolution : currentSolution;

        // Go over all vehicles
        for (int vId = 0; vId < agent.vehicles().size(); vId++) {

            // Compute number of actions
            ActionEntry c = current[vId];
            while (c.next != null) {
                c = c.next;
            }
            int nAction = c.time;

            // Compute cost for all possible task insert positions
            for (int iPickup = 0; iPickup <= nAction; iPickup++) { // FIXME not <= ? Check in centralized planner
                int iDelivery = iPickup + 1;

                boolean valid = true;
                boolean sameFound = true;

                while ((valid || sameFound) && iDelivery <= nAction + 1) { // FIXME not <= nAction + 1 Check in centralized planner

                    ActionEntry[] a = ActionEntry.copy(current); // FIXME only copy vehicles
                    valid = addTask(a[vId], agent.vehicles(), vId, t, iPickup, iDelivery); // FIXME can be done more
                    // efficiently
                    if (valid) {
                        // Compute cost
                        double cost = computeCost(a, agent.vehicles());
                        if (cost < lowestTotalCostFound) {
                            lowestTotalCostFound = cost;
                            bestPlan = a;
                        }

                    } else {
                        valid = sameFound;
                        sameFound = false;
                    }
                    iDelivery++;
                }
            }
        }

        if (opponent) {
            potentialOpponentSolution = bestPlan;
            potentialOpponentCost = lowestTotalCostFound;
        } else {
            potentialSolution = bestPlan;
            potentialCost = lowestTotalCostFound;
        }

        // Debug output
        if (VERBOSE) {
            System.out.println("Best Potential Plan:");
            for (int v = 0; v < vehicles.size(); v++) {
                System.out.println(bestPlan[v]);
            }
            System.out.println("Lowest potential cost: " + lowestTotalCostFound);
        }

        return lowestTotalCostFound;
    }

    /**
     * Add task to vehicle vid.
     *
     * @return true if the change is valid
     */
    private boolean addTask(ActionEntry a, List<Vehicle> vehicles, int vId, Task task, int iPickup, int iDelivery) {

        ActionEntry newPickup = new ActionEntry(task, true);
        ActionEntry newDelivery = new ActionEntry(task, false);
        // Note that the task here does not have correct reward yet, so it will have to be fixed later

        ActionEntry current = a;

        // Find correct index for pickup
        for (int p = iPickup; p > 0; p--) {
            if (current == null) {
                throw new Error("Pickup moment is not legal");
            }
            current = current.next;
        }

        // Add pickup entry
        current.add(newPickup);

        // Find correct index for delivery
        for (int d = iDelivery - iPickup; d > 0; d--) { // Need to be careful here
            if (current == null) {
                throw new Error("Delivery moment is not legal");
            }
            current = current.next;
        }

        // Add delivery entry
        current.add(newDelivery);

        // Update time and load
        return a.updateTimeAndLoad(vehicles.get(vId).capacity());
    }

    /**
     * Compute cost for a given schedule.
     *
     * @param actions a list of actionEntries
     * @param vehicles a list of vehicles
     * @return cost of the proposed schedule
     */
    private double computeCost(ActionEntry[] actions, List<Vehicle> vehicles) {
        double sum = 0;
        int i = 0;
        for (ActionEntry a : actions) {
            double cost = a.cost(vehicles.get(i).homeCity()) * vehicles.get(i).costPerKm();
            sum += cost;
            i++;
        }
        return sum;
    }

    /**
     * Phase 1 Helper function. Tries to mathematically approximate the amount of saving that can be made.
     * (following round only)
     *
     * The mathematical reasoning behind it is explained in our report.
     *
     * @param task a task is being auctioned
     * @return the expected amount of savings
     */
    private double savings(Task task) {

        double minSavings = Double.MAX_VALUE;

        for (Vehicle v : vehicles) {
            double sumOfMoveSavings = 0;

            List<City> path = task.pickupCity.pathTo(task.deliveryCity);
            City currentCity = task.pickupCity;

            for (City nextCity : path) {

                // For all moves on shortest path for task t
                double moveSavings = 0;

                moveSavings += currentCity.distanceTo(nextCity) * v.costPerKm();

                // Get likelihood of savings
                double likelihood = 0;

                for (Map.Entry<Task, Double> e : this.taskProbabilities.entrySet()) {
                    Task t = e.getKey();

                    // Check if t has edge in common
                    List<City> tPath = t.pickupCity.pathTo(t.deliveryCity);
                    tPath.add(t.pickupCity);

                    // Note that since we are at the very beginning we do not take already taken tasks into account
                    // This could be a noteworthy improvement, if we want to extend / mix Phase 1 with Phase 2
                    if (t.weight - v.capacity() < 0 && tPath.contains(nextCity) && tPath.contains(currentCity)) {

                        // We know the pickup cities are uniformly distributed.
                        likelihood += (1.0 / topology.cities().size()) * e.getValue();
                    }
                }

                moveSavings *= likelihood;
                sumOfMoveSavings += moveSavings;
            }

            // Conservatively pick lowest estimate across all vehicles
            if (sumOfMoveSavings < minSavings) {
                minSavings = sumOfMoveSavings;
            }
        }
        double probabilityThisIsLastTask = (1 - cumulativePoissonDistribution(N_EXPECTED_TASK, nAuctions));

        // System.out.println("Poisson p at "+ nAuctions + " = " + probabilityThisIsLastTask);
        return minSavings * probabilityThisIsLastTask;
    }
}
