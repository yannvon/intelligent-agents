package auction;

//the list of imports

import logist.agent.Agent;
import logist.behavior.AuctionBehavior;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A simple agent that:
 * 	- Checks marginal costs without reordering tasks, but picks best spot in sequence
 * 	- Adds something on top marginal cost randomly
 * 	- Performs centralized planning in the end to save some distance
 */
@SuppressWarnings("unused")
public class AuctionCentralizedPlanning implements AuctionBehavior {

	/*

	Further ideas:
	- Add some randomness -> hides intentions and other
	- When computing marginal cost, just check where in plan you could integrate new task, without reordering
	This then becomes lowest bid ready to take. Then do same for adversary and check what his marginal cost would be.
	Bid one below (?) this value if higher than personal one. -> IMPOSSIBLE
	- Centralized in the end.
	- Integrate probability of certain tasks, willing to take tasks at deficit ?

	 */
	public static final boolean VERBOSE = true;

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

    @Override
    public void setup(Topology topology, TaskDistribution distribution,
                      Agent agent) {

        this.topology = topology;
        this.distribution = distribution;
        this.agent = agent;
        this.vehicles = agent.vehicles();
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
    }

    @Override
    public Long askPrice(Task task) {

        if (VERBOSE) {
            System.out.println("--- TASK " + task.id + "---");
        }

        if (this.maxVehicleCapacity < task.weight)
            return null;

        /*
         * STEP 1: Find own marginal cost (under no reordering assumption)
         *
         * By going over all vehicles and all possible slots
         */
        double costWithNewTask = addingTaskCost(task);
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
        bid += random.nextDouble() * 1000;

        return (long) Math.round(bid);
    }

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {

        if (VERBOSE) {
            System.out.println("--- PLANNING PHASE ---");
            System.out.println("Agent " + agent.id() + " has tasks " + tasks);
        }

        // Invoke the centralized planner to save costs
        List<Plan> plans = planCentralized(tasks);

        if (VERBOSE) {
            double totalCost = 0;
            for (int i = 0; i < vehicles.size(); i++) {
                double c = plans.get(i).totalDistance() * vehicles.get(i).costPerKm();
                totalCost += c;
                System.out.println("Vehicle " + i + " cost: " + c);
            }
            System.out.println("Total cost: " + totalCost);
        }

        return plans;
    }


    private List<Plan> planCentralized(TaskSet tasks) {

        // Create instance of centralized planner
        CentralizedPlanning centralizedPlanning = new CentralizedPlanning();

        // Setup
        centralizedPlanning.setup(this.distribution, this.agent);

        // Plan
        List<Plan> plans = centralizedPlanning.plan(this.vehicles, tasks);

        return  plans;

    }


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

            for (int iPickup = 0; iPickup <= nAction; iPickup++) {   //FIXME not <= ?
                int iDelivery = iPickup + 1;

                boolean valid = true;
                boolean sameFound = true;

                while ((valid || sameFound) && iDelivery <= nAction + 1) { //FIXME not <= nAction + 1

                    ActionEntry[] a = ActionEntry.copy(currentSolution);    //FIXME only copy vehicles
                    valid = addTask(a[vId], agent.vehicles(), vId, t, iPickup, iDelivery); // FIXME can be done more efficiently
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

        // FIXME Effet de bords (not nice)
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
        // FIXME the task here does not have correct reward yet, so it will have to be fixed later

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


        // Update time and load FIXME check
        return a.updateTimeAndLoad(vehicles.get(vId).capacity());
    }

    /**
     * @param actions
     * @param vehicles
     * @return
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

