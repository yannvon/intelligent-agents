package auction;

//the list of imports
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

import javax.print.DocFlavor.STRING;

import helpers.Logger;
import logist.agent.Agent;
import logist.behavior.AuctionBehavior;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

/**
 * Deliberative for vehicle 0 !
 * 
 */
@SuppressWarnings("unused")
public class FirstStrategy implements AuctionBehavior {

	private static final boolean VERBOSE = false;

	/* Environment */
	TaskDistribution td;

	private static final double STARTING_RATIO = 0.9;
	private static final double ADDING_REWARD = 100;

	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;
	private Random random;
	private Vehicle vehicle;
	private int capacity;
	private Set<Task> currentTasks;

	private double ratio;

	private double nextCost;

	private double currentCost;



	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) {

		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;
		this.vehicle = agent.vehicles().get(0);
		this.capacity = vehicle.capacity();
		this.currentTasks = new HashSet<>();
		this.ratio = STARTING_RATIO;

		long seed = -9019554669489983951L * vehicle.homeCity().hashCode() * agent.id();
		this.random = new Random(seed);
	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {

		if (winner == agent.id()) {
			currentCost = nextCost;
			currentTasks.add(previous);
			ratio += 0.05;

			if (VERBOSE) {
				System.out.println("FirstStrat Win" + previous);
				System.out.println("Ratio " + ratio);
			}
		} else {
			ratio -= 0.15;

		}
		if (ratio < 1) {
			ratio = 1;
		}

	}

	@Override
	public Long askPrice(Task task) {
		System.out.println();
		long start = System.currentTimeMillis();

		Set<Task> nextTasks = new HashSet<>(currentTasks);

		nextTasks.add(task);

		Plan nextPlan = astarPlan(vehicle, nextTasks);
		nextCost = (nextPlan.totalDistance() * vehicle.costPerKm());
		long end = System.currentTimeMillis();

		if (VERBOSE) {
			System.out.println("--- TASK " + task.id + "---");
			System.out.println("----FIRST STRAT----");
			System.out.println("MarginalCost : " + String.format("%6.0f", nextCost - currentCost));
			System.out.println("Time it = " + String.format("%3.1f", (end - start) / 1000.) + "s");
			System.out.println();
		}

		return (long) ((nextCost - currentCost + ADDING_REWARD) * ratio);
	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
		long start = System.currentTimeMillis();

//		System.out.println("Agent " + agent.id() + " has tasks " + tasks);

		Plan planVehicle1 = astarPlan(vehicle, tasks);

		List<Plan> plans = new ArrayList<Plan>();
		plans.add(planVehicle1);
		while (plans.size() < vehicles.size())
			plans.add(Plan.EMPTY);

		long end = System.currentTimeMillis();

		double finalReward = +tasks.rewardSum() - (planVehicle1.totalDistance() * vehicle.costPerKm());

		System.out.println();
		System.out.println("Strat 1:");
		System.out.println("Time to compute plan: " + String.format("%3.1f", (end - start) / 1000.) + "s");
		System.out.println("Reward: " + String.format("%6.0f", finalReward));

		return plans;
	}

	private Plan astarPlan(Vehicle vehicle, Set<Task> nextTasks) {
		PriorityQueue<State> queue = new PriorityQueue<>();
		HashMap<State, State> visited = new HashMap<>();
		State finalState;

		State startState = new State(vehicle.getCurrentCity(), TaskSet.create(new Task[0]), nextTasks, null);
		queue.add(startState);

		int i = 0;

		while (true) {
			if (queue.isEmpty()) {
				// This should never happen
				throw new Error("No more enqueued states, but no goal state found");
			}

			State s = queue.poll();

			if (s.isGoalState()) {
				finalState = s;
				break;
			}

			if (!visited.containsKey(s) || s.cost() < visited.get(s).cost()) {
				visited.put(s, s);
				LinkedList<State> successors = computeSuccessors(s);
				queue.addAll(successors);
			}

			i++;
		}

		return constructPlanFromGoal(vehicle.getCurrentCity(), finalState);
	}

	/**
	 * Constructs a plan by iteratively traversing states starting from goal state.
	 *
	 * @param initialCity
	 * @param goal
	 * @return an optimal plan
	 */
	private Plan constructPlanFromGoal(City initialCity, State goal) {
		LinkedList<State> path = new LinkedList<>();

		State s = goal;
		while (s.parent != null) {
			path.addFirst(s);
			s = s.parent;
		}

		Plan p = new Plan(initialCity);
		int planSize = 0;

		for (State state : path) {
			// Check how state was attained
			if (state.deliver != null) {
				p.appendDelivery(state.deliver);
			} else if (state.pickup != null) {
				p.appendPickup(state.pickup);
			} else {
				p.appendMove(state.location);
			}
			planSize++;
		}
		System.out.println("Total number of actions in plan: " + planSize);
		System.out.println("Total number of km of path: " + p.totalDistance());

		return p;
	}

	/**
	 * Compute all possible Successors of a given state.
	 *
	 * @param s
	 * @return
	 */
	private LinkedList<State> computeSuccessors(State s) {

		LinkedList<State> successors = new LinkedList<>();

		// Deliver a task if available. Return only one successor if it is the case
		for (Task t : s.carriedTasks) {
			if (t.deliveryCity.equals(s.location)) {
				Set<Task> carriedTasks = new HashSet<>(s.carriedTasks);
				Set<Task> tasksToDeliver = new HashSet<>(s.tasksToDeliver);
				carriedTasks.remove(t);

				State suc = new State(s.location, carriedTasks, tasksToDeliver, s);
				suc.deliver = t; // indicate that this state was reached though delivery of t
				successors.add(suc);
				return successors;
			}
		}

		// Option 1: Travel to neighbor city
		for (City c : s.location.neighbors()) {
			State suc = new State(c, s.carriedTasks, s.tasksToDeliver, s, c.distanceTo(s.location));

			successors.add(suc);
		}

		// Option 2: Pickup a task if available{
		int weightSum = 0;
		for (Task ct : s.carriedTasks) {
			weightSum += ct.weight;
		}
		for (Task t : s.tasksToDeliver) {
			if (t.pickupCity.equals(s.location)) {
				// Check if can carry task
				if (t.weight + weightSum < capacity) {
					Set<Task> carriedTasks = new HashSet<>(s.carriedTasks);
					Set<Task> tasksToDeliver = new HashSet<>(s.tasksToDeliver);
					carriedTasks.add(t);
					tasksToDeliver.remove(t);

					State suc = new State(s.location, carriedTasks, tasksToDeliver, s);
					suc.pickup = t; // indicate that this state was reached through pickup of t
					successors.add(suc);
				}
			}
		}
		return successors;
	}

	private class State implements Comparable<State> {
		/*
		 * State representation
		 */
		private City location;
		private Set<Task> carriedTasks;
		private Set<Task> tasksToDeliver;

		/*
		 * Transition information
		 */
		private State parent;
		private Task pickup;
		private Task deliver;
		private double stepCost;

		/*
		 * Heuristic stored
		 */
		private double heuristicCost;

		public State(City location, Set<Task> carrying, Set<Task> nextTasks, State parent) {
			this(location, carrying, nextTasks, parent, 0);
		}

		public State(City location, Set<Task> carrying, Set<Task> todo, State parent, double stepCost) {
			this.location = location;
			this.carriedTasks = carrying;
			this.tasksToDeliver = todo;
			this.parent = parent;
			this.stepCost = stepCost;
			this.heuristicCost = cost() + heuristic();
		}

		public boolean isGoalState() {
			return tasksToDeliver.isEmpty() && carriedTasks.isEmpty();
		}

		public double cost() {
			if (parent == null) {
				return 0;
			} else {
				return parent.cost() + stepCost;
			}
		}

		public double heuristic() {
			double max = 0.;

			for (Task t : carriedTasks) {
				double dist = location.distanceTo(t.deliveryCity);
				if (max < dist) {
					max = dist;
				}
			}
			for (Task t : tasksToDeliver) {
				double dist = location.distanceTo(t.pickupCity) + t.pathLength();
				if (max < dist) {
					max = dist;
				}
			}

			return max;
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.location, this.carriedTasks, this.tasksToDeliver);
		}

		@Override
		public boolean equals(Object that) {
			if (!(that instanceof State)) {
				return false;
			}
			return this.location.equals(((State) that).location)
					&& (this.tasksToDeliver == ((State) that).tasksToDeliver
							|| this.tasksToDeliver.equals(((State) that).tasksToDeliver))
					&& (this.carriedTasks == ((State) that).carriedTasks
							|| this.carriedTasks.equals(((State) that).carriedTasks));
		}

		@Override
		public String toString() {
			return "location: " + location.toString() + " carriedtask:" + carriedTasks.size() + " todeliver: "
					+ tasksToDeliver.size();
		}

		@Override
		public int compareTo(State o) {
			return (int) (this.heuristicCost - o.heuristicCost);
		}
	}
}
