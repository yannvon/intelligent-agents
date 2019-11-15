package auction;

//the list of imports

import logist.agent.Agent;
import logist.behavior.AuctionBehavior;
import logist.plan.Action;
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
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 */
@SuppressWarnings("unused")
public class AuctionNoReorder implements AuctionBehavior {

	/*
	Some ideas:
	1)
	- Add some randomness -> hides intentions and other
	- When computing marginal cost, just check where in plan you could integrate new task, without reordering
	This then becomes lowest bid ready to take. Then do same for adversary and check what his marginal cost would be.
	Bid one below (?) this value if higher than personal one.
	- Centralized in the end.
	2) Integrate probability of certain tasks, willing to take tasks at deficit ?

	 */


    private Topology topology;
    private TaskDistribution distribution;
    private Agent agent;
    private Random random;
    private Vehicle vehicle;
    private City currentCity;

    private ActionEntry[] currentSolution;
    private double currentCost;

    private double potentialCost;
    private ActionEntry[] potentialSolution;

    @Override
    public void setup(Topology topology, TaskDistribution distribution,
                      Agent agent) {

        this.topology = topology;
        this.distribution = distribution;
        this.agent = agent;
        this.vehicle = agent.vehicles().get(0);
        this.currentCity = vehicle.homeCity();

        long seed = -9019554669489983951L * currentCity.hashCode() * agent.id();
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

            currentSolution = potentialSolution;
            currentCost = potentialCost;

            potentialSolution = null;
            currentCost = -1;
        }
    }

    @Override
    public Long askPrice(Task task) {

        if (vehicle.capacity() < task.weight)
            return null;

        /*
         * STEP 1: Find own marginal cost (under no reordering assumption)
         *
         * By going over all vehicles and all possible slots
         */
        double costWithNewTask = addingTaskCost(task);
        double marginalCost = currentCost - costWithNewTask;


        // Final bid
        // double ratio = 1.0 + (random.nextDouble() * 0.05 * task.id);
        // double bid = ratio * marginalCost;
        double bid = marginalCost;

        return (long) Math.round(bid);
    }

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {

//		System.out.println("Agent " + agent.id() + " has tasks " + tasks);

        Plan planVehicle1 = naivePlan(vehicle, tasks);

        List<Plan> plans = new ArrayList<Plan>();
        for (int vId = 0; vId < agent.vehicles().size(); vId++) {
            Plan plan = planFromActionEntry(agent.vehicles().get(vId), currentSolution[vId]);
            plans.add(plan);
        }

        return plans;
    }


    private Plan planFromActionEntry(Vehicle v, ActionEntry actionEntry) {
        City current = v.getCurrentCity();
        Plan plan = new Plan(current);

        ActionEntry next = actionEntry.next;
        while (next != null) {
            City nextCity = next.pickup ? next.task.pickupCity : next.task.deliveryCity;
            for (City city : current.pathTo(nextCity)) {
                plan.appendMove(city);
            }

            if (next.pickup) {
                plan.appendPickup(next.task);
            } else {
                plan.appendDelivery(next.task);
            }
            next = next.next;
            current = nextCity;
        }

        return plan;
    }


    private Plan naivePlan(Vehicle vehicle, TaskSet tasks) {
        City current = vehicle.getCurrentCity();
        Plan plan = new Plan(current);

        for (Task task : tasks) {
            // move: current city => pickup location
            for (City city : current.pathTo(task.pickupCity))
                plan.appendMove(city);

            plan.appendPickup(task);

            // move: pickup location => delivery location
            for (City city : task.path())
                plan.appendMove(city);

            plan.appendDelivery(task);

            // set current city
            current = task.deliveryCity;
        }
        return plan;
    }

    private double addingTaskCost(Task t) {

        double lowestTotalCostFound = Double.MAX_VALUE;
        ActionEntry[] bestPlan = null;

        // Go over all vehicles
        for (int i = 0; i < agent.vehicles().size(); i++) {

            // Compute number of actions
            ActionEntry c = currentSolution[i];
            while (c.next != null) {
                c = c.next;
            }
            int nAction = c.time;


            // Compute cost for all possible task insert positions

            for (int iPickup = 0; iPickup <= nAction; iPickup++) {   //FIXME not <= ?
                int iDelivery = 1;

                boolean valid = true;
                boolean sameFound = true;

                while ((valid || sameFound) && iDelivery <= nAction + 1) { //FIXME not <= nAction + 1

                    ActionEntry[] a = ActionEntry.copy(currentSolution);    //FIXME only copy vehicle
                    valid = addTask(a, agent.vehicles(), i, t, iPickup, iDelivery); // FIXME can be done more efficiently
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

        return lowestTotalCostFound;
    }

    /**
     * Add task to vehicle
     *
     * @return true if the change is valid
     */
    private boolean addTask(ActionEntry[] a, List<Vehicle> vehicles, int vId, Task task, int iPickup, int iDelivery) {

        ActionEntry newPickup = new ActionEntry(task, true);
        ActionEntry newDelivery = new ActionEntry(task, false);

        // TODO

        // if valid add to neighbors
        return a[vId].updateTimeAndLoad(vehicles.get(vId).capacity());
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

