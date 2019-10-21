package deliberative;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.PriorityQueue;

/* import table */

import logist.agent.Agent;
import logist.behavior.DeliberativeBehavior;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

/**
 * An optimal planner for one vehicle.
 */
@SuppressWarnings("unused")
public class DeliberativeAgent implements DeliberativeBehavior {

	/**
	 * Private helper class representing a state. In large parts similar to the
	 * reactive agent.
	 */
	private class State implements Comparable<State> {
		/*
		 * State representation
		 */
		private City location;
		private TaskSet carriedTasks;
		private TaskSet tasksToDeliver;

		/*
		 * Transitions information
		 */
		private State parent;
		private Task pickup;
		private Task deliver;
		private double stepCost;

		/*
		 * heuristic stored
		 */
		private double heuristicCost;

		public State(City location, TaskSet carrying, TaskSet todo, State parent) {
			this(location, carrying, todo, parent, 0);
		}

		public State(City location, TaskSet carrying, TaskSet todo, State parent, double stepCost) {
			this.location = location;
			this.carriedTasks = carrying;
			this.tasksToDeliver = todo;
			this.parent = parent;
			this.stepCost = stepCost;
			heuristicCost = cost() + heuristic();
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

	/*
	 * Goal state: tasksToDeliver.isEmpty()
	 * 
	 * Transitions:
	 */

	enum Algorithm {
		BFS, ASTAR
	}

	enum Transition {
		PICKUP, DELIVER, MOVE
	}

	/* Environment */
	Topology topology;
	TaskDistribution td;

	/* the properties of the agent */
	Agent agent;
	int capacity;
	double costPerKm;
	private TaskSet startingCarriedTasks;

	/* the planning class */
	Algorithm algorithm;

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {
		this.topology = topology;
		this.td = td;
		this.agent = agent;

		// initialize the planner
		capacity = agent.vehicles().get(0).capacity();
		String algorithmName = agent.readProperty("algorithm", String.class, "ASTAR");

		// Throws IllegalArgumentException if algorithm is unknown
		algorithm = Algorithm.valueOf(algorithmName.toUpperCase());

		// TODO
		costPerKm = agent.vehicles().get(0).costPerKm();

		startingCarriedTasks = null;
	}

	@Override
	public Plan plan(Vehicle vehicle, TaskSet tasks) {
		Plan plan;
		if (startingCarriedTasks == null) {
			startingCarriedTasks = tasks.clone();
			startingCarriedTasks.clear();
		}

		// Compute the plan with the selected algorithm.
		long start = System.currentTimeMillis();
		switch (algorithm) {
		case ASTAR:
			// ...
			plan = astarPlan(vehicle, tasks);
			break;
		case BFS:
			// ...
			plan = bfsPlan(vehicle, tasks);
			break;
		default:
			throw new AssertionError("Should not happen.");
		}
		long end = System.currentTimeMillis();

		System.out.println("Time to compute plan: " + ((end - start) / 1000.) + "s");
		return plan;
	}

	private Plan astarPlan(Vehicle vehicle, TaskSet tasks) {
		PriorityQueue<State> queue = new PriorityQueue<>();
		HashMap<State, State> visited = new HashMap<>();
		State finalState;

		State startState = new State(vehicle.getCurrentCity(), startingCarriedTasks, tasks, null);
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

	private Plan bfsPlan(Vehicle vehicle, TaskSet tasks) {

		// Initialize
		LinkedList<State> queue = new LinkedList<>();
		HashSet<State> visited = new HashSet<>();
		State finalState;

		// Create start state
		State startState = new State(vehicle.getCurrentCity(), startingCarriedTasks, tasks, null);
		queue.add(startState);

		// BFS algorithm
		int i = 0;
		while (true) {

			if (queue.isEmpty()) {
				throw new Error("No more enqueued states, but no goal state found");
			}

			State s = queue.pop();

			if (s.isGoalState()) {
				finalState = s;
				break;
			}

			if (!visited.contains(s)) {
				visited.add(s);
				LinkedList<State> successors = computeSuccessors(s);
				queue.addAll(successors);
			}
			i++;
		}

		// Construct Plan from finalState by walking successors
		return constructPlanFromGoal(vehicle.getCurrentCity(), finalState);

	}

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
		// FIXME
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
		
		// Option 3: Deliver a task if available
		for (Task t : s.carriedTasks) {
			if (t.deliveryCity.equals(s.location)) {
				TaskSet carriedTasks = s.carriedTasks.clone();
				TaskSet tasksToDeliver = s.tasksToDeliver.clone();
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

		// Option 2: Pickup a task if available
		for (Task t : s.tasksToDeliver) {
			if (t.pickupCity.equals(s.location)) {
				// Check if can carry task
				if (t.weight + s.carriedTasks.weightSum() < capacity) {
					TaskSet carriedTasks = s.carriedTasks.clone();
					TaskSet tasksToDeliver = s.tasksToDeliver.clone();
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

	@Override
	public void planCancelled(TaskSet carriedTasks) {

		if (!carriedTasks.isEmpty()) {
			this.startingCarriedTasks = carriedTasks.clone();
		}
	}

}
