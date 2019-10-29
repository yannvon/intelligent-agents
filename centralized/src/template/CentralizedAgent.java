package template;

import java.io.File;
//the list of imports
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import logist.LogistSettings;
import logist.agent.Agent;
import logist.behavior.CentralizedBehavior;
import logist.config.Parsers;
import logist.plan.Plan;
import logist.simulation.Vehicle;
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
public class CentralizedAgent implements CentralizedBehavior {

	private static final double STARTING_TEMPERATURE = 10_000.;
	private static final double LAMBDA = 0.95;
	private static final double SECURE_FACTOR = 0.75;

	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;
	private long timeout_setup;
	private long timeout_plan;
	private Random random;

	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) {

		// this code is used to get the timeouts
		LogistSettings ls = null;
		try {
			ls = Parsers.parseSettings("config" + File.separator + "settings_default.xml");
		} catch (Exception exc) {
			System.out.println("There was a problem loading the configuration file.");
		}

		// the setup method cannot last more than timeout_setup milliseconds
		timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
		// the plan method cannot execute more than timeout_plan milliseconds
		timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);
		System.out.println("Plan has " + timeout_plan + "ms to finish");

		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;
		this.random = new Random(2019);
	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
		long time_start = System.currentTimeMillis();

		/*
		 * Initialization
		 */
		ActionEntry[] currentSolution = new ActionEntry[vehicles.size()];
		for (int i = 0; i < vehicles.size(); i++) {
			currentSolution[i] = new ActionEntry(i);
		}

		// assign all task to vehicles with biggest capacity
		int maxCapacity = 0;
		int vMaxCap = 0;
		for (Vehicle v : vehicles) {
			if (v.capacity() > maxCapacity) {
				maxCapacity = v.capacity();
				vMaxCap = v.id();
			}
		}

		ActionEntry a = currentSolution[vMaxCap];
		for (Task t : tasks) {
			if (vMaxCap < t.weight) {
				throw new IllegalStateException("No vehicles can carry task:\n " + t.toString());
			}
			ActionEntry newA = new ActionEntry(t, true);
			a.add(newA);
			a = newA;
			newA = new ActionEntry(t, false);
			a = newA;
		}

		/*
		 * MAIN LOOP
		 */
		int iteration = 0;
		double temperature = STARTING_TEMPERATURE;
		ActionEntry[] best = currentSolution;
		double bestCost = computeCost(currentSolution, vehicles);
		double currentCost = bestCost;
		double currentTime = 0;
		do {

			List<ActionEntry[]> neighbors = chooseNeighbors(currentSolution, vehicles);
			if(neighbors.size()==0) {
				continue; //should not happen except if task is to big for vehicle
			}

			ActionEntry[] selectedN = selectRandomBestNeighbor(neighbors,vehicles);

			/*
			 * SIMULATED ANEALING
			 */
			double costN = computeCost(selectedN, vehicles);

			// if the cost is better change automatically
			if (costN < currentCost) {
				currentCost = costN;
				currentSolution = selectedN;
				// if this is the best cost found yet, saves it
				if (costN < bestCost) {
					bestCost = costN;
					best = selectedN;
				}
			} else {
				// compute probability to change
				double p = Math.exp((currentCost - costN) / temperature);
				if (p > random.nextDouble()) {
					currentCost = costN;
					currentSolution = selectedN;
				}
			}

			/*
			 * UPDATE TEMPERATURE, ITERATION and current time
			 */

			// FIXME don't know if measuring time is long or note, might remove condition
			//if (iteration % 100 == 0) {
			currentTime = System.currentTimeMillis();
			//}
			temperature *= LAMBDA; // FIXME a possibility would be to update the temperature depending on the time
									// left before timeout
			iteration++;
			
			if(iteration%100 ==0) {
				System.out.println("it: " + iteration +" time " + (currentTime - time_start) +" temp" +temperature);
				System.out.println("Best Cost: "+bestCost+ " current cost:" + currentCost);
			}

		} while (currentTime - time_start < SECURE_FACTOR * timeout_plan);// end the loop once we approach the end of
																			// timeout

		System.out.println("\nAlgo did " + iteration + " iterations");
		System.out.println("The final temperature was " + temperature);

		/*
		 * Construct plan
		 */

		List<Plan> plans = new ArrayList<Plan>();
		
		
		

		long time_end = System.currentTimeMillis();
		long duration = time_end - time_start;
		System.out.println("The plan was generated in " + duration + " milliseconds.");

		return plans;
	}

	private ActionEntry[] selectRandomBestNeighbor(List<ActionEntry[]> neighbors, List<Vehicle> vehicles) {
		// FIXME PIMP ME
		ActionEntry[] best = null;
		double bestCost = Double.POSITIVE_INFINITY;
		for(ActionEntry[] a: neighbors) {
			double cost = computeCost(a, vehicles);
			if(cost<bestCost) {
				bestCost = cost;
				best = a;
			}
		}

		return best;
	}

	/**
	 * Choose multiple neighbors of a solution
	 * 
	 * @param solution
	 * @param vehicles
	 * @return a list of valid neighbors of the solution
	 */
	private List<ActionEntry[]> chooseNeighbors(ActionEntry[] solution, List<Vehicle> vehicles) {

		List<ActionEntry[]> neighbors = new ArrayList<>();

		

		// select random vehicle with a task
		//FIXME what happen if no task are available
		int randomVid = random.nextInt(solution.length);
		while (solution[randomVid].next == null) {
			randomVid = random.nextInt(solution.length);
		}

		/*
		 * Change a task from one vehicle to another
		 */
		
		// move task to new vehicle
		for (int vId = 0; vId < vehicles.size(); vId++) {
			if (vId == randomVid) {
				continue;
			}
			ActionEntry[] a = copy(solution);
			ActionEntry toMoveP = a[randomVid].next;
			ActionEntry toMoveD = toMoveP.next;

			// Find the delivery of the task
			while (toMoveP.task != toMoveD.task) { // FIXME should be same pointer so no need for ".equals" => "==" is
													// faster
				toMoveD = toMoveD.next;
			}

			// remove them from first vehicle
			toMoveD.remove();
			toMoveP.remove();

			// add them to new vehicle
			a[vId].add(toMoveP);
			toMoveP.add(toMoveD);
			
			//update time and load, if valid add to neighbors
			boolean valid =a[vId].updateTimeAndLoad(vehicles.get(vId).capacity());
			valid &= a[randomVid].updateTimeAndLoad(vehicles.get(randomVid).capacity());
			if(valid) {
				neighbors.add(a);
			}

		}
		
		/*
		 * Changing task order
		 */
		int lenght = 1;
		ActionEntry c = solution[randomVid].next;
		while(c.next != null) {
			lenght ++;
		}
		
		//For all position
		for(int iP =1; iP<lenght-1;iP++) {
			int iD = iP +1;
			boolean valid =true;
			while(valid) {
				ActionEntry[] a = copy(solution);
				ActionEntry pick =a[randomVid].next;
				ActionEntry deli = pick.next;
				while(deli.task!= pick.task) {
					deli = deli.next;
				}
				
				//if the order is the same as solution
				if(iP == pick.time && iD == deli.time) {
					continue;
				}
				
				//change delivery order
				if(iD == deli.time) {
					ActionEntry next = a[randomVid];
					deli.remove();
					for(int i = 0; i<iD-1; i++) {
						next = next.next;
					}
					next.add(deli);
					
				}
				
				//change pick order
				if(iP != pick.time) {
					ActionEntry next = pick.next;
					for(int j =0; j< iP -1;j++) {
						next = next.next;
					}
					pick.remove();
					next.add(pick);
					
				}
				
				
				//if valid add to neighbors
				valid = a[randomVid].updateTimeAndLoad(vehicles.get(randomVid).capacity());
				if(valid) {
					neighbors.add(a);
				}
				
				
				
			}
			
			
			
		}
		

		return neighbors;
	}

	private ActionEntry[] copy(ActionEntry[] actions) {
		ActionEntry[] copy = new ActionEntry[actions.length];
		for (int i = 0; i < actions.length; i++) {
			copy[i] = actions[i].clone();
		}
		return copy;
	}

	private double computeCost(ActionEntry[] actions, List<Vehicle> vehicles) {
		double sum = 0;
		int i = 0;
		for (ActionEntry a : actions) {
			sum += a.cost(vehicles.get(i).homeCity()) * vehicles.get(i).costPerKm();
			i++;
		}
		return sum;
	}

	/**
	 * Class describing an action
	 *
	 */
	private static class ActionEntry {
		public ActionEntry next, prev;
		public int vehicleId; // FIXME necessary?
		public boolean pickup;
		public int time;
		public Task task;
		public int load;

		// header
		public ActionEntry(int vehicleId) {
			this.vehicleId = vehicleId;
			this.time = 0;
			this.load = 0;
		}

		// action
		public ActionEntry(Task t, boolean p) {
			this.task = t;
			this.pickup = p;
		}

		public void add(ActionEntry a) {
			a.next = this.next;
			this.next = a;
			if (a.next != null) {
				a.next.prev = a;
			}

			a.prev = this;

			a.vehicleId = vehicleId;
			// updateTime(); //FIXME do that only at end?
		}

		public double cost(City lastPos) {
			if (task == null) {
				return next == null ? 0 : next.cost(lastPos);
			}
			City nextCity = pickup ? task.pickupCity : task.deliveryCity;
			return lastPos.distanceTo(nextCity) + (next == null ? 0 : next.cost(nextCity));
		}

		public void remove() {
			// never remove header
			prev.next = next;
			if (next != null) {
				next.prev = prev;
			}
			// prev.updateTime(); //FIXME do that only at end?
		}

		/**
		 * Update time of each action and the load of vehicles after the action
		 * 
		 * @param maxLoad
		 *            the maximum load of the vehicle
		 * @return true if the schedule is valid, false otherwise
		 */
		public boolean updateTimeAndLoad(int maxLoad) {
			if (load > maxLoad) {
				return false;
			}
			if (next != null) {
				next.time = time + 1;

				if (next.pickup) {
					next.load = load + next.task.weight;
				} else {
					next.load = load - next.task.weight;
				}
				return next.updateTimeAndLoad(maxLoad);
			}
			return load == 0;
		}

		private ActionEntry clone(ActionEntry prev) {
			ActionEntry a = new ActionEntry(task, pickup);
			a.prev = prev;
			a.time = time;
			a.vehicleId = vehicleId;
			a.next = next.clone(a);
			a.load = load;

			return a;
		}

		@Override
		public ActionEntry clone() {
			return clone(null);
		}

	}

}
