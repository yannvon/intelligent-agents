package auction;

import logist.LogistSettings;
import logist.agent.Agent;
import logist.behavior.CentralizedBehavior;
import logist.config.Parsers;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

//the list of imports

/**
 * A class responsible for planning in a centralized way.
 * This was optimized for Assignment 4, and will feature some additional improvements.
 */
public class CentralizedPlanning  {

    private static final boolean VERBOSE = false;

    private static final double STARTING_TEMPERATURE = 100_000.;
    private static final double FINAL_TEMPERATURE = 100.;
    private static final double LAMBDA = FINAL_TEMPERATURE / STARTING_TEMPERATURE;
    private static final double SECURE_FACTOR = 0.9;
    private static final double PROBA_CHANGE_VEHICLE = 0.2;
    private static final int TEMP_DECREASE_FACTOR = 4;

    private TaskDistribution distribution;
    private Agent agent;
    private long timeout_setup;
    private long timeout_plan;
    private Random random;

    public void setup(TaskDistribution distribution, Agent agent) {

        // this code is used to get the timeouts
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
        System.out.println("Plan has " + timeout_plan + "ms to finish");

        this.distribution = distribution;
        this.agent = agent;
        this.random = new Random(2019);
    }

    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        long time_start = System.currentTimeMillis();

        // Return immediately if no tasks available
        if (tasks.isEmpty()) {
            List<Plan> plans = new ArrayList<>();
            for (int vId = 0; vId < vehicles.size(); vId++) {
                plans.add(Plan.EMPTY);
            }
            return plans;
        }


        /*
         * Initialization
         */
        ActionEntry[] currentSolution = initialSolution(vehicles, tasks);
        int iteration = 0;
        double temperature = STARTING_TEMPERATURE;
        double lastTemp = temperature;
        ActionEntry[] best = currentSolution;
        double bestCost = computeCost(currentSolution, vehicles);
        double currentCost = bestCost;
        double currentTime = 0.;
        double sumP = 0.;
        int countP = 0;

        /*
         * Stochastic Local Search algorithm for COP
         */

        do {
            ActionEntry[] selectedN;

            // Linearly decreasing temperature
            double temp_linear = STARTING_TEMPERATURE - (STARTING_TEMPERATURE - FINAL_TEMPERATURE) * (currentTime - time_start) / (timeout_plan * SECURE_FACTOR);
            double proba_linear = (temp_linear - FINAL_TEMPERATURE) / (STARTING_TEMPERATURE - FINAL_TEMPERATURE);

            if (random.nextDouble() < proba_linear) {
                // OPTION 1: Compute (almost) random neighbor
                selectedN = computeRandomNeighbor(currentSolution, vehicles);
            } else {
                // OPTION 2: Get best neighbor of neighborSet for a random task & vehicle.
                List<ActionEntry[]> neighbors = computeNeighbors(currentSolution, vehicles);
                if (neighbors.isEmpty()) {
                    currentTime = System.currentTimeMillis();
                    continue;
                }
                selectedN = selecBestNeighbor(neighbors, vehicles);
            }

            /*
             * Simulated Annealing component
             */
            double costN = computeCost(selectedN, vehicles);

            // OPTION 1: If the cost is better change automatically
            if (costN < currentCost) {
                currentCost = costN;
                currentSolution = selectedN;
                // If this is the best cost found yet, save it
                if (costN < bestCost) {
                    bestCost = costN;
                    best = selectedN;
                }
            } else {
                // OPTION 2: With exp decreasing probability change anyways
                double p = Math.exp((currentCost - costN) / temperature);
                sumP += p;
                if (p < 0.9 && p > 0.1) {
                    countP++;
                }

                if (p > random.nextDouble()) {
                    currentCost = costN;
                    currentSolution = selectedN;
                }
            }

            /*
             * End of step updates
             */
            currentTime = System.currentTimeMillis();
            temperature = STARTING_TEMPERATURE
                    * Math.pow(LAMBDA, (currentTime - time_start) / (timeout_plan * SECURE_FACTOR));
            iteration++;

            /*
             * Additional mechanism: Every time temperature decreases by factor "tempDecreaseFactor", restart at
             * current best solution.
             */
            if (lastTemp / TEMP_DECREASE_FACTOR > temperature) {
                lastTemp = temperature;
                currentCost = bestCost;
                currentSolution = best;
            }

            /*
             * Print useful info while running planning
             */
            if (VERBOSE && iteration % 2000 == 0) {
                System.out.println("it: " + String.format("%d", iteration) + "    time: "
                        + String.format("%5.0f", currentTime - time_start) + "     temp: "
                        + String.format("%5.0f", temperature));
                System.out.println("Best Cost: " + String.format("%6.0f", bestCost) + "    current cost:"
                        + String.format("%6.0f", currentCost));
                System.out.println("AVG p = " + String.format("%1.3f", sumP / 2000) + "    counter p (0.1,0.9) = "
                        + String.format("%d", countP));
                System.out.println();
                countP = 0;
                sumP = 0;
            }

            // end the loop once we approach the end of timeout
        } while (currentTime - time_start < SECURE_FACTOR * timeout_plan);

        /*
         * Print final result
         */
        if (VERBOSE) {
            System.out.println("\nAlgo did " + iteration + " iterations");
            System.out.println("The final temperature was " + temperature);
            System.out.println("Final Cost: " + bestCost);
            System.out.println("Max Cost: " + computeMaxCost(best, vehicles));
            System.out.println("Sum Cost: " + computeSumCost(best, vehicles));

            System.out.println("Plan:");
            for (Vehicle v : vehicles) {
                System.out.println(best[v.id()]);
            }
        }

        /*
         * Construct plan
         */
        List<Plan> plans = new ArrayList<Plan>();
        for (int vId = 0; vId < best.length; vId++) {
            City current = vehicles.get(vId).getCurrentCity();
            Plan plan = new Plan(current);

            ActionEntry next = best[vId].next;
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
            plans.add(plan);

        }

        long time_end = System.currentTimeMillis();
        long duration = time_end - time_start;

        if (VERBOSE) {
            System.out.println("The plan was generated in " + duration + " milliseconds.");
        }

        return plans;
    }

    /**
     * Create an initial solution
     *
     * @param vehicles list of vehicles
     * @param tasks    set of tasks
     * @return ActionEntry table
     */
    private ActionEntry[] initialSolution(List<Vehicle> vehicles, TaskSet tasks) {
        ActionEntry[] currentSolution = new ActionEntry[vehicles.size()];
        for (int i = 0; i < vehicles.size(); i++) {
            currentSolution[i] = new ActionEntry(i);
        }

        // assign all task to vehicles with biggest capacity
        int maxCapacity = 0;
        int vMaxCap = 0;
        for (Vehicle v : vehicles) {
            if (v.capacity() > maxCapacity) {
                maxCapacity = v.capacity();
                vMaxCap = v.id();
            }
        }

        ActionEntry a = currentSolution[vMaxCap];
        for (Task t : tasks) {
            if (maxCapacity < t.weight) {
                throw new IllegalStateException("No vehicles can carry task:\n " + t.toString());
            }
            ActionEntry newA = new ActionEntry(t, true);
            a.add(newA);
            a = newA;
            newA = new ActionEntry(t, false);
            a.add(newA);
            a = newA;
        }

        for (int i = 0; i < vehicles.size(); i++) {
            currentSolution[i].updateTimeAndLoad(Integer.MAX_VALUE);
        }

        return currentSolution;
    }

    /**
     * Choose multiple neighbors of a solution
     *
     * @return a list of valid neighbors of the solution
     */
    private List<ActionEntry[]> computeNeighbors(ActionEntry[] solution, List<Vehicle> vehicles) {

        List<ActionEntry[]> neighbors = new ArrayList<>();

        // select random vehicle with a task
        int randomVid = random.nextInt(solution.length);
        while (solution[randomVid].next == null) {
            randomVid = random.nextInt(solution.length);
        }

        /*
         * Change a task from one vehicle to another
         */
        for (int vId = 0; vId < vehicles.size(); vId++) {
            if (vId == randomVid) {
                continue;
            }
            ActionEntry[] a = ActionEntry.copy(solution);
            boolean valid = changeVTask(a, vehicles, randomVid, vId);
            if (valid) {
                neighbors.add(a);
            }

        }

        /*
         * Changing task order
         */
        // compute length
        ActionEntry c = solution[randomVid].next;
        while (c.next != null) {
            c = c.next;
        }
        int lenght = c.time;

        // pick random task
        int tId = random.nextInt(lenght) + 1;
        c = solution[randomVid].next;
        while (c.time != tId) {
            c = c.next;
        }

        Task t = c.task;

        // For all positions
        for (int iP = 1; iP < lenght; iP++) {
            int iD = iP + 1;
            boolean valid = true;
            boolean sameFound = true;

            while ((valid || sameFound) && iD <= lenght) {

                ActionEntry[] a = ActionEntry.copy(solution);
                valid = changeTaskOrder(a, vehicles, randomVid, t, iP, iD);
                if (valid) {
                    neighbors.add(a);
                } else {
                    valid = sameFound;
                    sameFound = false;
                }
                iD++;
            }
        }
        return neighbors;
    }

    /**
     * @param currentSolution
     * @param vehicles
     * @return
     */
    private ActionEntry[] computeRandomNeighbor(ActionEntry[] currentSolution, List<Vehicle> vehicles) {
        if (random.nextDouble() < PROBA_CHANGE_VEHICLE) {
            return computeRandomChangeV(currentSolution, vehicles);
        }
        return computeRandomChangeT(currentSolution, vehicles);
    }

    /**
     * @param solution
     * @param vehicles
     * @return
     */
    private ActionEntry[] computeRandomChangeT(ActionEntry[] solution, List<Vehicle> vehicles) {
        int randomVid = random.nextInt(solution.length);
        int i = 0;
        while (solution[randomVid].next == null || solution[randomVid].next.next.next == null) {
            randomVid = random.nextInt(solution.length);
            if (++i > 10000) {
                return solution;
            }
        }

        // compute length
        ActionEntry c = solution[randomVid].next;
        while (c.next != null) {
            c = c.next;
        }
        int lenght = c.time;

        // pick random task
        int tId = random.nextInt(lenght) + 1;
        c = solution[randomVid].next;
        while (c.time != tId) {
            c = c.next;
        }
        Task t = c.task;

        int j = 0;
        while (j++ < 10000) {
            int iP = random.nextInt(lenght - 1) + 1;
            int iD = random.nextInt(lenght - iP) + iP + 1;
            ActionEntry[] a = ActionEntry.copy(solution);
            boolean valid = changeTaskOrder(a, vehicles, randomVid, t, iP, iD);
            if (valid) {
                return a;
            }
        }
        return solution;
    }

    /**
     * @param solution
     * @param vehicles
     * @return
     */
    private ActionEntry[] computeRandomChangeV(ActionEntry[] solution, List<Vehicle> vehicles) {
        int randomVid = random.nextInt(solution.length);
        while (solution[randomVid].next == null) {
            randomVid = random.nextInt(solution.length);
        }
        int i = 0;
        while (i++ < 1000) {
            int vId = random.nextInt(vehicles.size());
            if (vId == randomVid) {
                continue;
            }
            ActionEntry[] a = ActionEntry.copy(solution);
            boolean valid = changeVTask(a, vehicles, randomVid, vId);
            if (valid) {
                return a;
            }
        }
        // no neighbors where valid
        return solution;
    }

    /**
     * Change the order of tasks inside a vehicle
     *
     * @return true if the change is valid
     */
    private boolean changeTaskOrder(ActionEntry[] a, List<Vehicle> vehicles, int randomVid, Task task, int iP, int iD) {

        ActionEntry pick = a[randomVid].next;
        while (pick.task != task) {
            pick = pick.next;
        }
        ActionEntry deli = pick.next;
        while (deli.task != pick.task) {
            deli = deli.next;
        }

        // if the order is the same as solution
        if (iP == pick.time && iD == deli.time) {
            iD++;
            return false;
        }
        pick.remove();
        deli.remove();
        a[randomVid].updateTimeAndLoad(vehicles.get(randomVid).capacity());

        ActionEntry next = a[randomVid];
        while (next.time != iP - 1) {
            next = next.next;
        }
        next.add(pick);
        pick.time = next.time;
        next = pick;
        while (next.time != iD - 2) {
            next = next.next;
        }
        next.add(deli);

        // if valid add to neighbors
        return a[randomVid].updateTimeAndLoad(vehicles.get(randomVid).capacity());
    }

    /**
     * move a task from one vehicle to another
     *
     * @param a
     * @param vehicles
     * @param from
     * @param to
     * @return true if the solution is valid
     */
    private boolean changeVTask(ActionEntry[] a, List<Vehicle> vehicles, int from, int to) {
        ActionEntry toMoveP = a[from].next;
        ActionEntry toMoveD = toMoveP.next;

        // Find the delivery of the task
        while (toMoveP.task != toMoveD.task) {
            // faster
            toMoveD = toMoveD.next;
        }

        // remove them from first vehicle
        toMoveD.remove();
        toMoveP.remove();

        // add them to new vehicle
        a[to].add(toMoveP);
        toMoveP.add(toMoveD);

        // update time and load, if valid add to neighbors
        boolean valid = a[to].updateTimeAndLoad(vehicles.get(to).capacity());
        valid &= a[from].updateTimeAndLoad(vehicles.get(from).capacity());
        return valid;
    }

    /**
     * Select one neighbors amongst all the neighbors
     *
     * @return the selected neighbor
     */
    private ActionEntry[] selecBestNeighbor(List<ActionEntry[]> neighbors, List<Vehicle> vehicles) {

        ActionEntry[] best = null;
        double bestCost = Double.POSITIVE_INFINITY;
        for (ActionEntry[] a : neighbors) {
            double cost = computeCost(a, vehicles);
            if (cost < bestCost) {
                bestCost = cost;
                best = a;
            }
        }

        return best;

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

    /**
     * @param actions
     * @param vehicles
     * @return the sum of vehicles cost
     */
    private double computeSumCost(ActionEntry[] actions, List<Vehicle> vehicles) {
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
     * @param actions
     * @param vehicles
     * @return the maximum vehicleCost
     */
    private double computeMaxCost(ActionEntry[] actions, List<Vehicle> vehicles) {
        int i = 0;
        double max = 0;
        for (ActionEntry a : actions) {
            double cost = a.cost(vehicles.get(i).homeCity()) * vehicles.get(i).costPerKm();
            if (max < cost) {
                max = cost;
            }
            i++;
        }
        return max;
    }
}
