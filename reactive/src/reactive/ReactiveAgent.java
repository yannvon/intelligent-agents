package reactive;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

import logist.agent.Agent;
import logist.behavior.ReactiveBehavior;
import logist.plan.Action;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.City;

public class ReactiveAgent implements ReactiveBehavior {

	public static double EPSILON = 0.001;

	/**
	 * Private helper class representing a state.
	 */
	private class State {
		private City l;
		private City t;


		State(City l, City t) {
			this.l = l;
			this.t = t;
		}

		@Override
		public int hashCode(){
			return Objects.hash(this.l, this.t);
		}

		@Override
		public boolean equals(Object that) {
			if (!(that instanceof State)) {
				return false;
			}
			return this.l.equals(((State) that).l) && (this.t == ((State) that).t || this.t.equals(((State) that).t));
		}

		@Override
		public String toString() {
			if (t == null)
				return l.toString() + ", null";
			else
				return l.toString() + ", " + t.toString();
		}
	}

	private int numActions;
	private Agent myAgent;

	private HashMap<State, Integer> Best;
	private HashMap<State, Double> V;

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {


		// Reads the discount factor from the agents.xml file.
		// If the property is not present it defaults to 0.95
		Double discount = agent.readProperty("discount-factor", Double.class,
				0.95);

		this.numActions = 0;
		this.myAgent = agent;

		int numberOfCities = topology.cities().size();
		this.Best = new HashMap<State, Integer>();
		this.V = new HashMap<State, Double>();

		ArrayList<ArrayList<State>> states = new ArrayList<ArrayList<State>>(numberOfCities);

		// Initialize data structures
		for(City currentCity: topology.cities()) {
			ArrayList<State> statesOfCity = new ArrayList<State>();

			for(City taskDestination : topology.cities()) {
				State newState = new State(currentCity, taskDestination);
				statesOfCity.add(newState);
				Best.put(newState, 0);
				V.put(newState, 0.0);
			}

			State noTaskState = new State(currentCity, null);
			statesOfCity.add(noTaskState);
			Best.put(noTaskState, 0);
			V.put(noTaskState, 0.0);

			states.add(statesOfCity);
		}

		/*
		 * Define strategy of reactive agent, l.e. determine V(S) and Best(S) through RLA algorithm
		 *
		 * NOTE: In the following the action "Accept task" is represented by -1. All other actions are characterized
		 * 		 by the integer index of the neighbor in the adjacency list.
		 */

		// As noted under 1.2.5, the cost per km stays constant for the reactive agent.
		int costPerKm = agent.vehicles().get(0).costPerKm();

		// Iterate until value doesn't change anymore
		boolean change;
		int iter = 0;
		do {
			change = false;

			// Iterate through all states
			for (ArrayList<State> statesOfCity: states) {
				for (State s : statesOfCity) {

					// We disregard the state where a task leads to same city.
					if (s.l.equals(s.t)) {
						continue;
					}

					City cityFrom = s.l;
					boolean taskAvailable = s.t != null;

					double currentBestOption = Double.NEGATIVE_INFINITY;
					int currentBestAction = Integer.MIN_VALUE;

					// Check all actions except "Accepting Task"
					int action = 0;
					for (City cityTo : cityFrom.neighbors()) {

						// Can't stay in same city
						if (cityFrom.equals(cityTo)) {
							continue;
						}

						double cost = costPerKm * cityFrom.distanceTo(cityTo);
						double reward = -cost;

						double q = getValueOfAction(cityTo, td, topology.cities(), reward, discount);

						if (q > currentBestOption) {
							currentBestOption = q;
							currentBestAction = action;
						}
						action++;
					}

					// If there is a task available, compare it to the other options
					if (taskAvailable) {
						City cityTo = s.t;
						double cost = costPerKm * cityFrom.distanceTo(cityTo);
						double reward = -cost + td.reward(cityFrom, cityTo); // Here the task reward is added

						double q = getValueOfAction(cityTo, td, topology.cities(), reward, discount);

						if (q > currentBestOption) {
							currentBestAction = -1;
							currentBestOption = q;
						}
					}

					// Verify if there has been enough change to justify another iteration
					if (Math.abs(currentBestOption - V.get(s)) > EPSILON) {
						change = true;
					}

					// Store the new values
					V.put(s, currentBestOption);
					Best.put(s, currentBestAction);
				}
			}
			System.out.println("Iteration: " + iter);
			iter++;
		}	while (change);
	}


	@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		Action action;
		City currentCity = vehicle.getCurrentCity();

		// If there is no available task, just pick best neighboring city.
		if (availableTask == null) {
			int a = Best.get(new State(currentCity, null));
			action = new Move(currentCity.neighbors().get(a));
		}
		// Otherwise pickup task if it is the best option.
		else {
			int a = Best.get(new State(currentCity, availableTask.deliveryCity));

			if (a == -1) {
				action = new Pickup(availableTask);
			} else {
				action = new Move(currentCity.neighbors().get(a));
			}
		}
		
		if (numActions >= 1 && (numActions<10 ||numActions%10 ==0)) {
			System.out.println("trained agent "+vehicle.name()+":\tACTION "+numActions+" \tPROFIT: "+myAgent.getTotalProfit()+
					" \taverage: "+(myAgent.getTotalProfit() / numActions)+"\tavg/km: "+(myAgent.getTotalProfit() / vehicle.getDistance()));
		}
		numActions++;
		return action;
	}

	private double getValueOfAction(City cityLocation, TaskDistribution td,
									List<City> cities, double reward, double discount){

		// Sum the probability of landing in given state * its value, over all possible states
		// Multiply this value by discount and add the reward gained in current step.
		double sum = 0;
		for (City task : cities) {
			// This state does not exist
			if (task.equals(cityLocation)) {
				continue;
			}
			sum += V.get(new State(cityLocation, task)) * td.probability(cityLocation, task);
		}
		// Careful: Also add value of state where no task available !
		sum += V.get(new State(cityLocation, null)) * td.probability(cityLocation, null);

		return reward + discount * sum;
	}
}
