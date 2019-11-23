package auction;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import experts.Arx;
import experts.Average;
import experts.Counter2;
import experts.EstimateOp;
import experts.Expert;
import experts.MaxMarginal;
import experts.Ratio;
import experts.RatioCustom;
import experts.Secure;
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

import auction.AuctionHelper;


/**
 * A agent that behaves in two phases:
 * TODO
 */
@SuppressWarnings("unused")
public class AuctionMultiplicativeWeightUpdate implements AuctionBehavior {

	private static final boolean VERBOSE = false;
	private static final boolean SHUFFLE = false;
	
	private static final double STARTING_RATIO = 0.5;
	private static final double STARTING_SECURE_FACTOR = 0.75;

	private static final double TAX = 10;

	private static final int PHASE1_END = 5;
	private static final int N_EXPECTED_TASK = 5;


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

		this.experts = new Expert[] {new MaxMarginal()};
		this.expertsBids = new Long[experts.length];
		this.weights = new double[experts.length];
		for(int i= 0; i < experts.length; i++) {
			weights[i] = 1.0/experts.length;
		}


		// Initializations for phase 1
		this.nAuctions = 0;
		this.taskProbabilities = new HashMap<>();

		for (City c1: topology.cities()) {
			for (City c2: topology.cities()) {

				Task t = new Task(0, c1, c2, 0, distribution.weight(c1, c2));
				this.taskProbabilities.put(t, distribution.probability(c1, c2));
			}
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

		if (nAuctions > PHASE1_END) {

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
		 * Find own marginal cost (under no reordering assumption)
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

		// Initialize bid
		long bid = Math.round(marginalCost);

		if (nAuctions < PHASE1_END) {
			/*
			 * --- PHASE 1 ---
			 *
			 * Find conservative estimate of savings on the future marginal cost when taking task t.
			 */

			double savings = savings(task);
			bid -= 0.2 * savings;	// FIXME constant

			System.out.println("Savings: " + savings);


		} else {
			/*
			 * --- PHASE 2 ---
			 *
			 * Use multiplicative weight update method to choose an expert advice.
			 */

			//compute all bids
			for(int eId =0; eId<experts.length;eId++) {
				expertsBids[eId]= experts[eId].bid(marginalCost, marginalOpponentCost);
			}

			bid = expertsBids[currentExpert];

		}


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

	private double savings(Task task) {

		double minSavings = Double.MAX_VALUE;

		for (Vehicle v : vehicles) {
			double sumOfMoveSavings = 0;

			List<City> path = task.pickupCity.pathTo(task.deliveryCity);
			City currentCity = task.pickupCity;

			for(City nextCity : path) {

				// For all moves on shortest path for task t
				double moveSavings = 0;

				moveSavings += currentCity.distanceTo(nextCity) * v.costPerKm();

				// Get likelihood of savings
				double likelihood = 0;
				int totalTasks = this.taskProbabilities.size();

				for (Map.Entry<Task, Double> e : this.taskProbabilities.entrySet()) {
					Task t = e.getKey();
					//FIXME use current weight FIXME make sure same move FIXME completely wrong now
					if (t.weight - v.capacity() < 0 && t.pickupCity.pathTo(t.deliveryCity).contains(nextCity)) {
						likelihood += (1.0 / totalTasks) * e.getValue();
					}
				}

				moveSavings *= likelihood;
				double probabilityThisIsLastTask = (1 - AuctionHelper.cumulativePoissonDistribution(N_EXPECTED_TASK, nAuctions));
				sumOfMoveSavings += moveSavings * probabilityThisIsLastTask;
				System.out.println("Poission p at "+ nAuctions + " = " + probabilityThisIsLastTask);
			}

			// Conservatively pick lowest estimate across all vehicles
			if (sumOfMoveSavings < minSavings) {
				minSavings = sumOfMoveSavings * nAuctions;
			}
		}

		return minSavings;
	}
}
