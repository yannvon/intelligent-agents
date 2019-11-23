package auction;

import java.io.File;
import java.util.List;
import java.util.Random;

import experts.Average;
import experts.Expert;
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

/**
 * A very simple auction agent that assigns all tasks to its first vehicles and
 * handles them sequentially.
 */
@SuppressWarnings("unused")
public class WeigthedMajority implements AuctionBehavior {

	/*
	 * Some ideas: 1) - Add some randomness -> hides intentions and other - When
	 * computing marginal cost, just check where in plan you could integrate new
	 * task, without reordering This then becomes lowest bid ready to take. Then do
	 * same for adversary and check what his marginal cost would be. Bid one below
	 * (?) this value if higher than personal one. - Centralized in the end. 2)
	 * Integrate probability of certain tasks, willing to take tasks at deficit ?
	 * 
	 */
	private static final boolean VERBOSE = false;
	private static final boolean SHUFFLE = false;
	
	

	private static final double STARTING_RATIO = 0.5;
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
	
	
	private int currentExpert;
	private Expert[] experts;
	private Long[] expertsBids;
	
	CentralizedPlanning centralizedPlanning;
	private long timeout_bid;
	
	private double marginalCost;
	
	private double[] weights ;



	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) {
		LogistSettings ls = null;
		try {
			ls = Parsers.parseSettings("config" + File.separator + "settings_auction.xml");
		} catch (Exception exc) {
			System.out.println("There was a problem loading the configuration file.");
		}

		// the setup method cannot last more than timeout_setup milliseconds
		timeout_bid = ls.get(LogistSettings.TimeoutKey.BID);

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
		centralizedPlanning = new CentralizedPlanning();

		// Setup
		centralizedPlanning.setup(this.distribution, this.agent);
		this.marginalCost = 0;
		
		this.currentExpert = 0;
		this.experts = new Expert[] {new Average()};
		this.expertsBids = new Long[experts.length];
		this.weights = new double[experts.length];
		for(int i= 0;i<experts.length;i++) {
			weights[i] = 1.0/experts.length;
		}
		
	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {
		
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
		
		Long opBid;
		if(agent.id() == 0 && bids.length>1) {
			opBid = bids[1];
		}else {
			opBid = bids[0];
		}
		double sumW = 0.0;
		for(int eId =0; eId<experts.length;eId++) {
			boolean expertWin = opBid == null | opBid>expertsBids[eId];
			experts[eId].update(expertWin, opBid);
			
			double reward =expertWin? expertsBids[eId] -marginalCost:0.0;
			double maxReward = opBid-marginalCost;
			double multipli;
			if(opBid ==null || opBid<0l) {
				multipli = reward>0.0 ? reward: 1;
			}else if(reward<0){
				multipli = 1.0;
			}else {
				 multipli= 1.0 + reward/(maxReward);
			};		
			weights[eId]*=multipli ;
			sumW += weights[eId];
		}
		int max = 0;
		for(int eId =0; eId<experts.length;eId++) {
			weights[eId] = weights[eId]/sumW;
			if(weights[max]<weights[eId]) {
				max = eId;
			}
		}
		currentExpert = max;

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
		//Our marginal cost
		double costWithNewTask = addingTaskCost(task);
		if(SHUFFLE) {
			centralizedPlanning.shuffle(vehicles, potentialSolution, timeout_bid/2);
			costWithNewTask = computeCost(potentialSolution, vehicles);
		}
		marginalCost = costWithNewTask - currentCost;
		
		//opponent marginal cost
		double opponentCost = addingTaskCost(task, true);
		if(SHUFFLE) {
			centralizedPlanning.shuffle(vehicles, potentialOpponentSolution, timeout_bid/2);
			opponentCost = computeCost(potentialOpponentSolution, vehicles);
		}
		double marginalOpponentCost = opponentCost - currentOpponentCost;
		
		//compute all bids
		for(int eId =0; eId<experts.length;eId++) {
			expertsBids[eId]= experts[eId].bid(marginalCost, marginalOpponentCost);
		}
		
		long bid = expertsBids[currentExpert];

		if (VERBOSE) {
			System.out.println("Current cost: " + currentCost);
			System.out.println("Cost with potential Task:" + costWithNewTask);
			System.out.println("Marginal cost of adding Task: " + marginalCost);
			System.out.println();
			System.out.println("sim op cost: " + currentOpponentCost);
			System.out.println("sim op Cost with potential Task:" + opponentCost);
			System.out.println("sim op Marginal cost: " + marginalOpponentCost);
			
			System.out.println("\nBid by "+experts[currentExpert].name() + " :" + bid);
		}

		

		return bid;
	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {

		List<Plan> plans = planCentralized(tasks);

		AuctionHelper.displayPerformance(getClass().toString(), tasks, plans, vehicles);

		return plans;
	}

	private List<Plan> planCentralized(TaskSet tasks) {


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
		for (ActionEntry a : actions) {
			double cost = a.cost(vehicles.get(i).homeCity()) * vehicles.get(i).costPerKm();
			sum += cost;
			i++;
		}
		return sum;
	}

}
