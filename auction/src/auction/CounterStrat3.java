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

import java.util.*;

/**
 * A very simple auction agent that assigns all tasks to its first vehicles and
 * handles them sequentially.
 */
@SuppressWarnings("unused")
public class CounterStrat3 implements AuctionBehavior {

	/*
	 * Some ideas: 1) - Add some randomness -> hides intentions and other - When
	 * computing marginal cost, just check where in plan you could integrate new
	 * task, without reordering This then becomes lowest bid ready to take. Then do
	 * same for adversary and check what his marginal cost would be. Bid one below
	 * (?) this value if higher than personal one. - Centralized in the end. 2)
	 * Integrate probability of certain tasks, willing to take tasks at deficit ?
	 * 
	 */
	public static final boolean VERBOSE = false;

	private static final double STARTING_RATIO = 0.;
	private static final double STARTING_SECURE_FACTOR = 0.75;

	private static final double TAX = 10;

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

	private double ratio;
	private double secureFactor;

	private double opponentRatio;

	private boolean maximizingReward = false;

	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) {

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

		// Init Action entries
		this.currentSolution = new ActionEntry[agent.vehicles().size()];
		for (int i = 0; i < agent.vehicles().size(); i++) {
			currentSolution[i] = new ActionEntry(i);
		}

		this.currentOpponentSolution = new ActionEntry[agent.vehicles().size()];
		for (int i = 0; i < agent.vehicles().size(); i++) {
			currentOpponentSolution[i] = new ActionEntry(i);
		}

		ratio = STARTING_RATIO;
		opponentRatio = 0;
		secureFactor = STARTING_SECURE_FACTOR;
	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {

		if (bids.length == 2) {
			Long opBid = agent.id() == 0 ? bids[1] : bids[0];
			if (opBid != null) {
				double opMarginalCost = potentialOpponentCost - currentOpponentCost;
				double simRatio = opBid / opMarginalCost;

				if (simRatio < 3.0 && simRatio > 0) { // TO NOT HAVE TO BIG RATIO FOR SMALL MARGINAL COST
					if (opponentRatio < 0.1) {
						opponentRatio = opBid / opMarginalCost;
					} else {
						opponentRatio = (opponentRatio * 0.7) + (0.3 * (opBid / opMarginalCost));
					}
				}
			}
		}

		if (winner == agent.id()) {

			// Option 1: Auction was won

			if (potentialSolution == null || currentCost < 0) {
				throw new Error("Unexpected behavior, no bid was made, yet bid was won");
			}

			currentSolution = potentialSolution;
			currentCost = potentialCost;

			potentialSolution = null;
			potentialCost = -1;

			ratio += 0.05;
			if (maximizingReward) {
				secureFactor *= 1.1;
			}
		} else {
			// Option 2: Auction was lost

			if (!maximizingReward) {
				ratio -= 0.15;
			} else {
				secureFactor *= 0.85;
			}

			currentOpponentSolution = potentialOpponentSolution;
			currentOpponentCost = potentialOpponentCost;

			potentialOpponentSolution = null;
			potentialOpponentCost = -1;

		}

		if (ratio < 1.) {
			ratio = 1.;
		}

		if (VERBOSE) {
			System.out.println();
			System.out.println("BIDS " + previous.id + " : ");
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
			System.out.println();
			System.out.println("AGENT " + agent.id() + "--- TASK " + task.id + "---");
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

		double opponentCost = addingTaskCost(task, true);
		double marginalOpponentCost = opponentCost - currentOpponentCost;

		if (VERBOSE) {
			System.out.println("Current cost: " + currentCost);
			System.out.println("Cost with potential Task:" + costWithNewTask);
			System.out.println("Marginal cost of adding Task: " + marginalCost);
			System.out.println("ratio: " + ratio);
			System.out.println();
			System.out.println("sim op cost: " + currentOpponentCost);
			System.out.println("sim op Cost with potential Task:" + opponentCost);
			System.out.println("sim op Marginal cost: " + marginalOpponentCost);
			System.out.println("op sim ratio: " + opponentRatio);
		}

		double bid = (marginalCost + TAX) * ratio;

		double opBid = marginalOpponentCost * opponentRatio;
		maximizingReward = bid < opBid * secureFactor;
		if (maximizingReward) {
			bid = opBid * secureFactor;
		}

		return (long) Math.round(bid);
	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {

		List<Plan> plans = planCentralized(tasks);

		AuctionHelper.displayPerformance("Counter2 with centralized planning", tasks, plans, vehicles);

		return plans;
	}

	private List<Plan> planCentralized(TaskSet tasks) {

		// Create instance of centralized planner
		CentralizedPlanning centralizedPlanning = new CentralizedPlanning();

		// Setup
		centralizedPlanning.setup(this.distribution, this.agent);

		// Plan
		List<Plan> plans = centralizedPlanning.plan(this.vehicles, tasks);

		return plans;

	}

	private double addingTaskCost(Task t) {
		return addingTaskCost(t, false);
	}

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

			for (int iPickup = 0; iPickup <= nAction; iPickup++) { // FIXME not <= ?
				int iDelivery = iPickup + 1;

				boolean valid = true;
				boolean sameFound = true;

				while ((valid || sameFound) && iDelivery <= nAction + 1) { // FIXME not <= nAction + 1

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

		// FIXME Effet de bords (not nice)
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
	 * Add task to vehicles
	 *
	 * @return true if the change is valid
	 */
	private boolean addTask(ActionEntry a, List<Vehicle> vehicles, int vId, Task task, int iPickup, int iDelivery) {

		ActionEntry newPickup = new ActionEntry(task, true);
		ActionEntry newDelivery = new ActionEntry(task, false);
		// FIXME the task here does not have correct reward yet, so it will have to be
		// fixed later

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
