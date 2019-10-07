package qlearning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

public class ReactiveLoic implements ReactiveBehavior {

	private int numActions;
	private Agent myAgent;

	private HashMap<State, Double> vecVal;
	private HashMap<State, City> vecAct;

	public class State {
		private City current;
		private City task;

		public State(City current, City task) {
			this.current = current;
			this.task = task;
		}

		public boolean equals(Object other) {
			if (other == null) {
				return false;
			}

			return current.equals(((State) other).current)
					&& (task == ((State) other).task || task.equals(((State) other).task));
		}

		public int hashCode() {
			return Objects.hash(current, task);
		}

		public String toString() {
			return "[ " + current.toString() + " , " + task.toString() + " ]";
		}
	}

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {

		// Reads the discount factor from the agents.xml file.
		// If the property is not present it defaults to 0.95
		Double discount = agent.readProperty("discount-factor", Double.class, 0.95);
		Double iterations = agent.readProperty("it", Double.class, 100.0);

		this.numActions = 0;
		this.myAgent = agent;

		vecVal = new HashMap<ReactiveLoic.State, Double>();
		vecAct = new HashMap<ReactiveLoic.State, Topology.City>();

		List<City> cities = topology.cities();
		List<State> tempStates = new ArrayList<ReactiveLoic.State>();
		Vehicle vehicle = agent.vehicles().get(0);

		for (City cit : cities) {
			for (City t : cities) {
				State st = new State(cit, t);
				vecVal.put(st, 1.0);
				vecAct.put(st, null);
				tempStates.add(st);
			}
			State st = new State(cit, null);
			vecVal.put(st, 1.0);
			vecAct.put(st, null);
			tempStates.add(st);
		}

		int it = 0;
		do {
			it++;

			for (State s : tempStates) {

				double maxV = Double.NEGATIVE_INFINITY;
				City bestAc = null;

				/*
				 * For all movements
				 */
				for (City m : s.current.neighbors()) {
					if (m.equals(s.task) || m.equals(s.current)) {
						continue;
					}
					double reward = 0;
					for (City t2 : cities) {
						reward += td.probability(m, t2) * vecVal.get(new State(m, t2));
					}
					reward *= discount;
					reward = -m.distanceTo(s.current) * vehicle.costPerKm();

					if (maxV < reward) {
						maxV = reward;
						bestAc = m;
					}
				}

				/*
				 * if accept task
				 */
				if (s.task != null) {
					double reward = 0;
					for (City t2 : cities) {
						reward += td.probability(s.task, t2) * vecVal.get(new State(s.task, t2));
					}
					reward *= discount;
					reward = td.reward(s.current, s.task) - s.task.distanceTo(s.current) * vehicle.costPerKm();

					if (maxV < reward) {
						maxV = reward;
						bestAc = s.task;
					}
				}
				if (bestAc == null) {
					System.out.println("error" + s.toString());
				}
				vecVal.put(s, maxV);
				vecAct.put(s, bestAc);

			}
			System.out.println("Training it :" + it);
		} while (it < iterations);

	}

	@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		Action action;

		State st = null;

		if (availableTask == null) {
			st = new State(vehicle.getCurrentCity(), null);
			action = new Move(vecAct.get(st));
		} else {
			st = new State(vehicle.getCurrentCity(), availableTask.deliveryCity);
			City next = vecAct.get(st);
			action = next.equals(availableTask.deliveryCity) ? new Pickup(availableTask) : new Move(next);
		}

		if (numActions >= 1 && (numActions < 10 || numActions % 10 == 0)) {
			System.out.println("REACTIVE LOIC: \tACTION " + numActions + " \tPROFIT: " + myAgent.getTotalProfit()
					+ " \taverage: " + (myAgent.getTotalProfit() / numActions) + "\tavg/km: "
					+ (myAgent.getTotalProfit() / vehicle.getDistance()));
		}
		numActions++;

		return action;
	}
}