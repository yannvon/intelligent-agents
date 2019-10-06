package template;

import java.util.*;

import logist.simulation.Vehicle;
import logist.agent.Agent;
import logist.behavior.ReactiveBehavior;
import logist.plan.Action;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.City;

public class ReactiveTemplateYann implements ReactiveBehavior {
	public static double THRESHOLD = 0.001;

	public class State {
		private City i;
		private City t;

		public State(City i, City t) {
			this.i = i;
			this.t = t;
		}
		@Override
		public int hashCode(){
			return Objects.hash(this.i, this.t);
		}

		@Override
		public boolean equals(Object that) {
			if (!(that instanceof State)) {
				return false;
			}
			return this.i.equals(((State) that).i) && (this.t == ((State) that).t || this.t.equals(((State) that).t));
		}
		@Override
		public String toString() {
			if (t == null)
				return i.toString() + ", null";
			else
				return i.toString() + ", " + t.toString();
		}
	}

	private Random random;
	private double pPickup;
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

		this.random = new Random();
		this.pPickup = discount;
		this.numActions = 0;
		this.myAgent = agent;


		int numberOfCities = topology.cities().size();
		this.Best = new HashMap<>();
		this.V = new HashMap<>();

		ArrayList<ArrayList<State>> states = new ArrayList<>(numberOfCities);

		for(City currentCity: topology.cities()) {
			ArrayList<State> statesOfCity = new ArrayList<>();

			for(City taskDestination : topology.cities()) {
				State newState = new State(currentCity, taskDestination);
				statesOfCity.add(newState);
				Best.put(newState, 0);
				V.put(newState, 0.0);
			}

			State noTaskState = new State(currentCity, null);
			statesOfCity.add(noTaskState);
			Best.put(noTaskState, -1);
			V.put(noTaskState, Double.MIN_VALUE);

			// Fill arrays
			states.add(statesOfCity);
		}

		/*
		 * Define strategy of reactive agent, i.e. V(S) and Best(S)
		 */


		// Action0: go to neighbor 0, Action1: go to neghbor 1,etc.., Action = -1 : accept task.


		int costPerKm = agent.vehicles().get(0).costPerKm();
		boolean change;
		int iter = 0;
		do {
			change = false;

			for (ArrayList<State> statesOfCity: states) {
				for (State s : statesOfCity) {

					if (s.i.equals(s.t)) {
						continue;
					}

					City cityFrom = s.i;
					boolean taskAvailable = s.t != null;

					double currentBestOption = Double.NEGATIVE_INFINITY;
					int currentAction = Integer.MIN_VALUE;


					// Check all actions except delivery
					int action = 0;
					for (City cityTo : cityFrom.neighbors()) {
						if (cityFrom.equals(cityTo)) {
							continue;
						}

						int indexOfCityTo = topology.cities().indexOf(cityTo);
						double cost = costPerKm * cityFrom.distanceTo(cityTo);
						double reward = -cost;

						double sum = 0;

						// sum over all states action can lead to
						for (State neighborState : states.get(indexOfCityTo)) {
							sum += V.get(neighborState) * td.probability(cityFrom, cityTo);
						}

						double q = reward + discount * sum;

						if (q > currentBestOption) {
							currentBestOption = q;
							currentAction = action;
						}
						action++;
					}

					if (taskAvailable) {
						City cityTo = s.t;
						double cost = costPerKm * cityFrom.distanceTo(cityTo);
						double reward = -cost + td.reward(cityFrom, cityTo);

						double sum = 0;
						int indexOfCityTo = topology.cities().indexOf(cityTo);

						// sum over all states action can lead to
						for (State neighborState : states.get(indexOfCityTo)) {
							sum += V.get(neighborState) * td.probability(cityFrom, cityTo);
						}

						double q = reward + discount * sum;

						if (q > currentBestOption) {
							currentAction = -1;
							currentBestOption = q;
						}
					}

					if (Math.abs(currentBestOption - V.get(s)) > THRESHOLD) {
						change = true;
					}
					V.put(s, currentBestOption);
					Best.put(s, currentAction);
					System.out.println("V Option: " + currentBestOption + " action: " + currentAction);
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

		if (availableTask == null) {
			int a = Best.get(new State(currentCity, null));
			action = new Move(currentCity.neighbors().get(a));
		} else {
			int a = Best.get(new State(currentCity, availableTask.deliveryCity));

			if (a == -1) {
				action = new Pickup(availableTask);
			} else {
				action = new Move(currentCity.neighbors().get(a));
			}
		}
		
		if (numActions >= 1) {
			System.out.println("The total profit after "+numActions+" actions is "+myAgent.getTotalProfit()+" (average profit: "+(myAgent.getTotalProfit() / (double)numActions)+")");
		}
		numActions++;
		
		return action;
	}
}
