package reactive;

import java.util.Random;

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

public class DummyAgent implements ReactiveBehavior {

	private Random random;
	private double rAccept;
	private int numActions;
	private Agent myAgent;

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {

		// Reads the discount factor from the agents.xml file.
		// If the property is not present it defaults to 0.95
		Double discount = agent.readProperty("discount-factor", Double.class,
				0.95);

		this.random = new Random();
		this.rAccept = discount;
		this.numActions = 0;
		this.myAgent = agent;
	}

	@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		Action action;

		if (availableTask == null || availableTask.reward/availableTask.pickupCity.distanceTo(availableTask.deliveryCity) < rAccept ) {
			City currentCity = vehicle.getCurrentCity();
			action = new Move(currentCity.randomNeighbor(random));
		} else {
			action = new Pickup(availableTask);
		}
		
		if (numActions >= 1 && (numActions<10 ||numActions%10 ==0)) {
			System.out.println("dummy agent "+vehicle.name()+":\t\tACTION " + numActions + " \t PROFIT " + myAgent.getTotalProfit()
					+ " \tavg/ac: " + (myAgent.getTotalProfit() / numActions) + "\tavg/km: "+(myAgent.getTotalProfit() / vehicle.getDistance()));
		}
		numActions++;
		
		return action;
	}
}