package template;

import java.io.File;
//the list of imports
import java.util.ArrayList;
import java.util.List;

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

    private Topology topology;
    private TaskDistribution distribution;
    private Agent agent;
    private long timeout_setup;
    private long timeout_plan;
    
    @Override
    public void setup(Topology topology, TaskDistribution distribution,
            Agent agent) {
        
        // this code is used to get the timeouts
        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config" + File.separator + "settings_default.xml");
        }
        catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }
        
        // the setup method cannot last more than timeout_setup milliseconds
        timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
        // the plan method cannot execute more than timeout_plan milliseconds
        timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);
        
        this.topology = topology;
        this.distribution = distribution;
        this.agent = agent;
    }

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        long time_start = System.currentTimeMillis();

        
        /*
         * Initialization
         */
        ActionEntry[] actions = new ActionEntry[vehicles.size()];
        for(int i = 0; i<vehicles.size();i++) {
        	actions[i] = new ActionEntry(i);
        }
        
        //assign all task to vehicles with biggest capacity
        int maxCapacity = 0;
        int vMaxCap = 0;
        for(Vehicle v: vehicles) {
        	if(v.capacity()>maxCapacity) {
        		maxCapacity = v.capacity();
        		vMaxCap = v.id();
        	}
        }
        
        ActionEntry a = actions[vMaxCap];
        for(Task t : tasks) {
        	if(vMaxCap<t.weight) {
        		throw new IllegalStateException("No vehicles can carry task:\n "+ t.toString());
        	}
	    	ActionEntry newA = new ActionEntry(t,true); 
	    	a.add(newA);
	    	a= newA;
	    	newA = new ActionEntry(t,false);
	    	a = newA;
        }
        
        /*
         * MAIN LOOP
         */
        int iteration = 0;
        ActionEntry[] best = actions;
        double bestCost = computeCost(actions,vehicles);
        do {
        	ActionEntry[][] neighbors = chooseNeighbors(a);
        	
        	//SIMULATED ANEALING!!!!!
        	
        	iteration ++;
        }while(iteration <1000);
        System.out.println("Algo did "+iteration+" iterations");
        
        
        /*
         * Construct plan
         */
        
        List<Plan> plans = new ArrayList<Plan>();
        
        
        
        
        
        long time_end = System.currentTimeMillis();
        long duration = time_end - time_start;
        System.out.println("The plan was generated in " + duration + " milliseconds.");
        
        return plans;
    }

    private ActionEntry[][] chooseNeighbors(ActionEntry a) {
		//TODO
		return null;
	}

	private double computeCost(ActionEntry[] actions,List<Vehicle> vehicles) {
		double sum =0;
		int i=0;
		for(ActionEntry a:actions) {
			sum += a.cost(vehicles.get(i++).homeCity());
		}
		return sum;
	}

	private class ActionEntry {
    	public ActionEntry next,prev;
    	public int vehicleId;
    	public boolean pickup;
    	public int time;
    	public Task task;
    	
    	//header
    	public ActionEntry(int vehicleId) {
    		this.vehicleId = vehicleId;
    		this.time = 0;
    	}
    	
    	//action
    	public ActionEntry(Task t, boolean p) {
    		this.task = t;
    		this.pickup = p;
    	}
    	
    	
    	public void add(ActionEntry a) {
    		a.next = this.next;
    		this.next = a;
    		if(a.next != null) {
    			a.next.prev = a;
    		}
    		
    		a.prev = this;
    		
    		a.vehicleId = vehicleId;
    		//updateTime(); //FIXME do that only at end?
    	}
    	
    	public double cost(City lastPos) {
    		if(task == null) {
    			return next == null? 0:next.cost(lastPos);
    		}
    		City nextCity = pickup? task.pickupCity:task.deliveryCity;
    		return lastPos.distanceTo(nextCity) + (next==null?0:next.cost(nextCity));
    	}
    	
    	public void remove() {
    		prev.next = next;
    		next.prev = prev;
    		//prev.updateTime(); //FIXME do that only at end?
    	}
    	
		public void updateTime() {
			if(next != null) {
				next.time = time +1;
				next.updateTime();
			}
			
		}
		
		private ActionEntry clone(ActionEntry prev) {
			ActionEntry a = new ActionEntry(task,pickup);
			a.prev = prev;
			a.time = time;
			a.vehicleId = vehicleId;
			a.next = next.clone(a);
			
			return a;
		}
		
		public ActionEntry clone() {
			
			return clone(null);
		}
    	
    }
    
}
