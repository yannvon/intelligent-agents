package auction;

//the list of imports
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import auction.AuctionHelper;
import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.agent.Agent;
import logist.simulation.Vehicle;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

/**
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 * 
 */
@SuppressWarnings("unused")
public class AuctionMultiAgents implements AuctionBehavior {

	private static final int MAX_TASK_DELIB = 10;
	
	private static final boolean VERBOSE = true;

	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;
	private Random random;
	private Vehicle vehicle;
	private City currentCity;

	private List<AuctionBehavior> experts;
	private int currentExpertId;
	private int nbTasks;

	private Long[] expertsBids;
	
	private long[] expertBenef;

	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) {

		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;
		this.vehicle = agent.vehicles().get(0);
		this.currentCity = vehicle.homeCity();

		long seed = -9019554669489983951L * currentCity.hashCode() * agent.id();
		this.random = new Random(seed);

		currentExpertId = 0;
		nbTasks = 0;
		

		experts = new ArrayList<>();
		experts.add(new FirstStrategy());
		experts.add(new CounterStrat2());
		experts.add(new CounterStrat());
		experts.add(new AuctionCentralizedPlanning());

		for (AuctionBehavior ex : experts) {
			ex.setup(topology, distribution, agent);
		}
		expertsBids = new Long[experts.size()];
		expertBenef =  new long[experts.size()];

	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {

		for (AuctionBehavior ex : experts) {
			ex.auctionResult(previous, winner, bids);
		}

		if (bids.length == 2) {
			Long opBid = agent.id() == 0 ? bids[1] : bids[0];
			if (opBid != null) {
				long maxBenef = Long.MIN_VALUE;
				
				for(int i = 0; i<experts.size();i++) {
					if(expertsBids[i] == null){
						expertBenef[i] -= opBid;
					}else {
						if(expertsBids[i]<opBid) {
							expertBenef[i] += expertsBids[i];
						}else {
							expertBenef[i] -= opBid;
						}
					}
					
					if(expertBenef[i]>maxBenef) {
						maxBenef = expertBenef[i];
						currentExpertId = i;
					}
				}
			}

		}

	}

	@Override
	public Long askPrice(Task task) {

		int i = 0;
		for (AuctionBehavior ex : experts) {

			// skip
			if (nbTasks > MAX_TASK_DELIB && i == 0) {
				expertsBids[i] = -10_000_000l;
			} else {
				expertsBids[i] = ex.askPrice(task);
			}
			i++;
		}
		
		if(VERBOSE) {
			System.out.println();
			System.out.println("BIDS= " + Arrays.toString(expertsBids));
			System.out.println("Current expert: " +currentExpertId + " " + experts.get(currentExpertId).getClass().toString() );
		}
		return expertsBids[currentExpertId];
	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {

		return experts.get(currentExpertId).plan(vehicles, tasks);
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
}
