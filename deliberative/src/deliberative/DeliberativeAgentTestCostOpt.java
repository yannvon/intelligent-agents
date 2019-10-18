package deliberative;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Scanner;

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
public class DeliberativeAgentTestCostOpt implements DeliberativeBehavior {

	/**
	 * Private helper class representing a state.
	 * In large parts similar to the reactive agent.
	 */
	private class State {
		private City location;
		private TaskSet carriedTasks;
		private TaskSet tasksToDeliver;

		private State parent;
		private Task pickup;
		private Task deliver;
		
		private int cost=0;
		

	   public int cost() {
		   if(parent == null) {
			   return 0;
		   }else {
			   return parent.cost() + cost;
		   }
	   }

		public State(City location, TaskSet carrying, TaskSet todo, State parent) {
			this.location = location;
			this.carriedTasks = carrying;
			this.tasksToDeliver = todo;
			this.parent = parent;
		}

		public boolean isGoalState() {
			return tasksToDeliver.isEmpty() && carriedTasks.isEmpty();
		}

		@Override
		public int hashCode(){
			return Objects.hash(this.location, this.carriedTasks, this.tasksToDeliver);
		}

		@Override
		public boolean equals(Object that) {
			if (!(that instanceof State)) {
				return false;
			}
			return this.location.equals(((State) that).location)
					&& (this.tasksToDeliver == ((State) that).tasksToDeliver || this.tasksToDeliver.equals(((State) that).tasksToDeliver))
					&& (this.carriedTasks == ((State) that).carriedTasks || this.carriedTasks.equals(((State) that).carriedTasks));
		}

		@Override
		public String toString() {
			return "location: "+location.toString() +" carriedtask:" + carriedTasks.size()+ " todeliver: "+ tasksToDeliver.size();
		}
	}

	/*
	Goal state: tasksToDeliver.isEmpty()

	Transitions:
	 */

	enum Algorithm { BFS, ASTAR }
	enum Transition {PICKUP, DELIVER, MOVE}
	
	/* Environment */
	Topology topology;
	TaskDistribution td;
	
	/* the properties of the agent */
	Agent agent;
	int capacity;
	double costPerKm;
	TaskSet carriedTasks;


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
	}
	
	@Override
	public Plan plan(Vehicle vehicle, TaskSet tasks) {
		Plan plan;

		// Compute the plan with the selected algorithm.
		switch (algorithm) {
		case ASTAR:
			// ...
			plan = naivePlan(vehicle, tasks);
			break;
		case BFS:
			// ...
			plan = bfsPlan(vehicle, tasks);
			break;
		default:
			throw new AssertionError("Should not happen.");
		}		
		return plan;
	}

	private Plan bfsPlan(Vehicle vehicle, TaskSet tasks) {

		// Initialize
		LinkedList<State> queue = new LinkedList<>();
		HashMap<State,State> visited = new HashMap<>();
		LinkedList<State> successors = new LinkedList<>();
		State finalState=null;

		// Create start state
		TaskSet emptyTaskSet = tasks.clone();
		emptyTaskSet.clear();
		State startState = new State(vehicle.getCurrentCity(), emptyTaskSet, tasks, null);
		queue.add(startState);

		// BFS algorithm
		int i = 0;

		int nFin = 0;
		int j = 0;
		Scanner sc = new Scanner(System.in);
		while((i< 1_000_000 || finalState == null) && !queue.isEmpty()) {
			State s = queue.pop();
			if (s.isGoalState()) {
				
				if(finalState == null || finalState.cost()>s.cost()) {
					finalState = s;
					System.out.println(s.cost());
					nFin ++;
				}
				//break;
			}
			if (!visited.containsKey(s)){
				visited.put(s,s);
				successors = computeSuccessors(s);
				
				
				/*System.out.println("\n\n");
				System.out.println("IT :" + i);
				System.out.println("STATE");
				System.out.println(s.toString());
				System.out.println("Sucessors" + successors.size());
				for(State ns:successors) {
					System.out.println(ns.toString());
				}*/
				

				for(State suc : successors) {
					queue.addLast(suc);
				}
				
			}else {
				State s2 = visited.get(s);
				if(s2.cost()>s.cost()) {
					successors = computeSuccessors(s);
					for(State suc : successors) {
						queue.addLast(suc);
					}
				}
				
				j++;
				if(j%1000 ==0) {
					System.out.println("visited "+ j+ " times");
				}
				
			}

			i++;
			//sc.nextLine();
			
		}
		System.out.println("Iteration = " + i + "\nfinalStates = " + nFin);

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

		for (State state : path) {
			// Check how state was attained
			if (state.deliver != null){
				p.appendDelivery(state.deliver);
			}
			else if (state.pickup != null){
				p.appendPickup(state.pickup);
			}
			else {
				p.appendMove(state.location);
			}
		}
		// FIXME
		System.out.println("Total number of distance Units of path: " + p.totalDistanceUnits());
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
		for(Task t : s.carriedTasks) {
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
	

		// Option 2: Pickup a task if available
		for(Task t : s.tasksToDeliver) {
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


		// Option 1: Travel to neighbor city
		for(City c : s.location.neighbors()) {
			State suc = new State(c, s.carriedTasks, s.tasksToDeliver, s);
			suc.cost = (int) ( c.distanceTo(s.location));
			successors.add(suc);
		}
		return successors;
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

	@Override
	public void planCancelled(TaskSet carriedTasks) {
		
		if (!carriedTasks.isEmpty()) {
			// This cannot happen for this simple agent, but typically
			// you will need to consider the carriedTasks when the next
			// plan is computed.
		}
	}
}
