package auction;

//the list of imports
import helpers.ActionEntry;
import helpers.AuctionHelper;
import helpers.CentralizedPlanning;
import helpers.Logger;
import logist.LogistSettings;
import logist.agent.Agent;
import logist.behavior.AuctionBehavior;
import logist.config.Parsers;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;

import java.io.File;
import java.util.List;
import java.util.Random;


/**
 * A simple agent that:
 * 	- Checks marginal costs without reordering tasks, but picks best spot in sequence
 * 	- Performs SLS on top of that
 * 	- Adds a constant value on top of marginal cost
 * 	- Performs centralized planning in the end to augment reward
 */
@SuppressWarnings("unused")
public class AuctionRandomBaseline implements AuctionBehavior {

	public static final boolean VERBOSE = false;
    public static final boolean LOG = true;

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
    private ActionEntry potentialPickupActionEntry;
    private ActionEntry potentialDeliveryActionEntry;
    CentralizedPlanning centralizedPlanning ;
    
    private long timeout_bid,timeout_setup,timeout_plan;

    private Logger log;
    private Long sumBidsWon;

    @Override
    public void setup(Topology topology, TaskDistribution distribution,
                      Agent agent) {

        this.topology = topology;
        this.distribution = distribution;
        this.agent = agent;
        this.vehicles = agent.vehicles();
        
    	LogistSettings ls = null;
		try {
			ls = Parsers.parseSettings("config" + File.separator + "settings_auction.xml");
		} catch (Exception exc) {
			System.out.println("There was a problem loading the configuration file.");
		}

		// the setup method cannot last more than timeout_setup milliseconds
		timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
		// the plan method cannot execute more than timeout_plan milliseconds
		timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);
		
		timeout_bid = ls.get(LogistSettings.TimeoutKey.BID);
        
        this.maxVehicleCapacity = Integer.MIN_VALUE;
        for (Vehicle v: vehicles) {
            this.maxVehicleCapacity =  (v.capacity() > this.maxVehicleCapacity) ? v.capacity() : this.maxVehicleCapacity;
        }

        long seed = -9019554669489983951L * this.hashCode() * agent.id();
        this.random = new Random(seed);

        // Init Action entries
        this.currentSolution = new ActionEntry[agent.vehicles().size()];
        for (int i = 0; i < agent.vehicles().size(); i++) {
            currentSolution[i] = new ActionEntry(i);
        }
        // Create instance of centralized planner
        centralizedPlanning = new CentralizedPlanning();

        // Setup
        centralizedPlanning.setup(distribution, agent);

        // Create log file
        if (LOG) {
            this.log = new Logger(this.getClass().getName() + "_log.csv");
            this.sumBidsWon = 0L;
        }
    }

    @Override
    public void auctionResult(Task previous, int winner, Long[] bids) {
        if (winner == agent.id()) {

            // Option 1: Auction was won
            if (VERBOSE) {
                System.out.println("Auction " + previous.id + " won");
            }
            if (potentialSolution == null || currentCost < 0) {
                throw new Error("Unexpected behavior, no bid was made, yet bid was won");
            }

            currentSolution = potentialSolution;
            currentCost = potentialCost;

            potentialSolution = null;
            potentialCost = -1;

            if (LOG) {
                sumBidsWon += bids[winner];
            }
        } else {
            // Option 2: Auction was lost
            if (VERBOSE) {
                System.out.println("Auction lost");
            }
        }
        if (VERBOSE) {
            for (int i = 0; i < bids.length; i++) {
                System.out.print("Bid " + i + ": " + bids[i] + " ");
            }
            System.out.println();
            System.out.flush();
        }
        if (LOG) {
            double currentReward = sumBidsWon - currentCost;
            log.logToFile(previous.id, currentReward);
        }
    }

    @Override
    public Long askPrice(Task task) {

        if (VERBOSE) {
            System.out.println("--- TASK " + task.id + "---");
        }

        if (this.maxVehicleCapacity < task.weight)
            return null;

        /*
         * Find own marginal cost (under no reordering assumption), then use SLS
         *
         * By going over all vehicles and all possible slots
         */
        double costWithNewTask = addingTaskCost(task);
        
        centralizedPlanning.shuffle(vehicles, currentSolution,timeout_bid/4);
        
        
        double marginalCost = costWithNewTask - currentCost;

        if (VERBOSE) {
            System.out.println("Current cost: " + currentCost);
            System.out.println("Cost with potential Task:" + costWithNewTask);
            System.out.println("Marginal cost of adding Task: " + marginalCost);
        }


        // Final bid
        // double ratio = 1.0 + (random.nextDouble() * 0.05 * task.id);
        // double bid = ratio * marginalCost;
        double bid = marginalCost;
        bid += random.nextDouble() * 10;

        return Math.round(bid);
    }

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {

        List<Plan> plans = planCentralized(tasks);

        AuctionHelper.displayAndLogPerformance(getClass().toString(), tasks, plans, vehicles, log);

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
                centralizedPlanning.shuffle(this.vehicles, currentSolution, Math.round(timeout_plan * 0.9));

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

        double lowestTotalCostFound = Double.MAX_VALUE;
        ActionEntry[] bestPlan = null;

        // Go over all vehicles
        for (int vId = 0; vId < agent.vehicles().size(); vId++) {

            // Compute number of actions
            ActionEntry c = currentSolution[vId];
            while (c.next != null) {
                c = c.next;
            }
            int nAction = c.time;


            // Compute cost for all possible task insert positions

            for (int iPickup = 0; iPickup <= nAction; iPickup++) {
                int iDelivery = iPickup + 1;

                boolean valid = true;
                boolean sameFound = true;

                while ((valid || sameFound) && iDelivery <= nAction + 1) {

                    ActionEntry[] a = ActionEntry.copy(currentSolution);
                    valid = addTask(a[vId], agent.vehicles(), vId, t, iPickup, iDelivery);
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

        potentialSolution = bestPlan;
        potentialCost = lowestTotalCostFound;

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
     * Add task to vehicles
     *
     * @return true if the change is valid
     */
    private boolean addTask(ActionEntry a, List<Vehicle> vehicles, int vId, Task task, int iPickup, int iDelivery) {

        ActionEntry newPickup = new ActionEntry(task, true);
        ActionEntry newDelivery = new ActionEntry(task, false);

        ActionEntry current = a;

        // Find correct index for pickup
        for(int p = iPickup; p > 0; p--) {
            if (current == null) {
                throw new Error("Pickup moment is not legal");
            }
            current = current.next;
        }

        // Add pickup entry
        current.add(newPickup);

        // Find correct index for delivery
        for(int d = iDelivery - iPickup; d > 0; d--) { // Need to be careful here
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
        double max = 0;
        for (ActionEntry a : actions) {
            double cost = a.cost(vehicles.get(i).homeCity()) * vehicles.get(i).costPerKm();
            sum += cost;
            if (max < cost) {
                max = cost;
            }
            i++;
        }
        return sum;
    }

}

