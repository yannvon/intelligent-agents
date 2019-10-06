import java.util.ArrayList;

import uchicago.src.sim.analysis.DataSource;
import uchicago.src.sim.analysis.OpenSequenceGraph;
import uchicago.src.sim.analysis.Sequence;
import uchicago.src.sim.engine.BasicAction;
import uchicago.src.sim.engine.Schedule;
import uchicago.src.sim.engine.SimInit;
import uchicago.src.sim.engine.SimModelImpl;
import uchicago.src.sim.gui.DisplaySurface;
import uchicago.src.sim.gui.Object2DDisplay;
import uchicago.src.sim.util.SimUtilities;

/**
 * Class that implements the simulation model for the rabbits grass simulation.
 * This is the first class which needs to be setup in order to run Repast
 * simulation. It manages the entire RePast environment and the simulation.
 *
 * @author
 */

public class RabbitsGrassSimulationModel extends SimModelImpl {

	private RabbitsGrassSimulationSpace space;
	
	private OpenSequenceGraph amountOfObjects;

	private Schedule schedule;

	private DisplaySurface displaySurf;

	private ArrayList<RabbitsGrassSimulationAgent> agents,toRemove;
	private int toAdd;

	// DEFAULT VALUES
	private static final int GRID_SIZE = 20, NUM_RABBITS = 5, NUM_GRASS = 10, GROW_RATE = 5, BIRTH_THRESHOLD = 30;

	/*
	 * PARAMETERS
	 */

	// COMPULSORY DO NOT TOUCH
	private int gridSize = GRID_SIZE, numInitRabbits = NUM_RABBITS, numInitGrass = NUM_GRASS,
			grassGrowthRate = GROW_RATE, birthThreshold = BIRTH_THRESHOLD;

	// ADDED
	private int minEnergy = 10, maxEnergy = 30, grassCal=10;



	/**
	 * MAIN: run rabbit grass simulation
	 * 
	 * @param args
	 *            args[0] parameters file, args[1] true if batch mode
	 */
	public static void main(String[] args) {

		System.out.println("Rabbit skeleton");

		SimInit init = new SimInit();
		RabbitsGrassSimulationModel model = new RabbitsGrassSimulationModel();
		// Do "not" modify the following lines of parsing arguments
		if (args.length == 0) // by default, you don't use parameter file nor batch mode
			init.loadModel(model, "", false);
		else
			init.loadModel(model, args[0], Boolean.parseBoolean(args[1]));

	}
	
	
	class RabbitsCounter implements DataSource, Sequence {

	    public Object execute() {
	      return new Double(getSValue());
	    }

	    public double getSValue() {
	      return (double)agents.size();
	    }
	  }
	
	class GrassCounter implements DataSource, Sequence {

	    public Object execute() {
	      return new Double(getSValue());
	    }

	    public double getSValue() {
	      return (double)space.countGrass();
	    }
	  }
	

	public void setup() {

		space = null;
		schedule = new Schedule(1);

		agents = new ArrayList<>();
		toRemove = new ArrayList<>();
		toAdd =0;
		 
		 if (amountOfObjects != null){
			 amountOfObjects.dispose();
		    }
		 amountOfObjects = null;
		 

		if (displaySurf != null) {
			displaySurf.dispose();
		}
		displaySurf = null;

		displaySurf = new DisplaySurface(this, "Rabbit Model Window 1");
		amountOfObjects = new OpenSequenceGraph("Amount Of Rabbits and Grass",this);

		registerDisplaySurface("Rabbit Model Window 1", displaySurf);
		this.registerMediaProducer("Rabbits and grass", amountOfObjects);


	}

	public void begin() {
		buildModel();
		buildSchedule();
		buildDisplay();

		displaySurf.display();
		amountOfObjects.display();

	}

	public void buildModel() {
		space = new RabbitsGrassSimulationSpace(gridSize,numInitGrass,grassCal);
		
		for(int i = 0;i<numInitRabbits;i++) {
			addNewAgent();
		}
		
		

	    for(RabbitsGrassSimulationAgent a : agents){
	      a.report();
	    }
	}

	/**
	 * Build Schedule
	 */
	public void buildSchedule() {
		
		/**
		 *	Action at each step
		 */
		class RabbitGrassStep extends BasicAction {
		      public void execute() {
		    	  
		    	  // Remove dead rabbits
		    	agents.removeAll(toRemove);
		    	for(RabbitsGrassSimulationAgent deadAg : toRemove) {
		    		space.removeAgent(deadAg.getX(), deadAg.getY());
		    	}
		    	toRemove.clear();
		    	for(int i = 0;i<toAdd;i++) {
		    		addNewAgent();
		    	}
		    	toAdd=0;
		    	
		    	  
		    	//execute step for each rabbit
		        SimUtilities.shuffle(agents);
		        for(RabbitsGrassSimulationAgent ag : agents){
		          ag.step();
		          //ag.report();
		          if(ag.getEnergy()<=0) {
		        	  toRemove.add(ag);
		          }
		          if(ag.getEnergy()>birthThreshold) {
		        	  ag.reproduce(minEnergy, maxEnergy);
		        	  toAdd++;
		          }
		        }
		        
		        for(int i=0;i<grassGrowthRate;i++) {
		        	space.addGrass();
		        }
		        
		        
		        
		        displaySurf.updateDisplay();
		        
		        
		      }
		    }

		    schedule.scheduleActionBeginning(0, new RabbitGrassStep());
		    
		    class Counters extends BasicAction {
		        public void execute(){
		          amountOfObjects.step();
		        }
		      }

		      schedule.scheduleActionAtInterval(10, new Counters());
	}

	/**
	 *  Display
	 */
	public void buildDisplay() {


		Object2DDisplay displayColor = new Object2DDisplay(space.getGrassSpace());

		displaySurf.addDisplayable(displayColor, "Field");
		
	    Object2DDisplay displayAgents = new Object2DDisplay(space.getAgentSpace());
	    displayAgents.setObjectList(agents);
	    displaySurf.addDisplayable(displayAgents, "Agents");
	    
	    amountOfObjects.addSequence("Rabbits In Space", new RabbitsCounter());
	    amountOfObjects.addSequence("Grass In Space", new GrassCounter());
	}

	private void addNewAgent() {
		RabbitsGrassSimulationAgent a = new RabbitsGrassSimulationAgent(minEnergy, maxEnergy);
		if(space.addAgent(a)) {
			agents.add(a);
		}
	}

	public String[] getInitParam() {
		// Parameters to be set by users via the Repast UI slider bar
		// Do "not" modify the parameters names provided in the skeleton code, you can
		// add more if you want
		String[] params = { "GridSize", "NumInitRabbits", "NumInitGrass", "GrassGrowthRate", "BirthThreshold",
				/*
				 * ADDED
				 */
				"MaxEnergy", "MinEnergy","GrassCal" };
		return params;
	}

	public String getName() {
		return "Rabbit Grass Simulation";
	}

	public Schedule getSchedule() {
		return schedule;
	}

	/**
	 * @return the gridSize
	 */
	public int getGridSize() {
		return gridSize;
	}

	/**
	 * @param gs
	 *            the gridSize to set
	 */
	public void setGridSize(int gs) {
		this.gridSize = gs;
	}

	/**
	 * @return the numInitRabbits
	 */
	public int getNumInitRabbits() {
		return numInitRabbits;
	}

	/**
	 * @param numIR
	 *            the numInitRabbits to set
	 */
	public void setNumInitRabbits(int numIR) {
		numInitRabbits = numIR;
	}

	/**
	 * @return the numInitGrass
	 */
	public int getNumInitGrass() {
		return numInitGrass;
	}

	/**
	 * @param numIG
	 *            the numInitGrass to set
	 */
	public void setNumInitGrass(int numIG) {
		numInitGrass = numIG;
	}

	/**
	 * @return the grassGrowthRate
	 */
	public int getGrassGrowthRate() {
		return grassGrowthRate;
	}

	/**
	 * @param ggr
	 *            the grassGrowthRate to set
	 */
	public void setGrassGrowthRate(int ggr) {
		grassGrowthRate = ggr;
	}

	/**
	 * @return the birthThreshold
	 */
	public int getBirthThreshold() {
		return birthThreshold;
	}

	/**
	 * @param bth
	 *            the birthThreshold to set
	 */
	public void setBirthThreshold(int bth) {
		birthThreshold = bth;
	}

	/**
	 * @return the maxEnergy
	 */
	public int getMaxEnergy() {
		return maxEnergy;
	}

	/**
	 * @param maxEnergy
	 *            the maxEnergy to set
	 */
	public void setMaxEnergy(int maxEnergy) {
		this.maxEnergy = maxEnergy;
	}

	/**
	 * @return the minEnergy
	 */
	public int getMinEnergy() {
		return minEnergy;
	}

	/**
	 * @param minEnergy
	 *            the minEnergy to set
	 */
	public void setMinEnergy(int minEnergy) {
		this.minEnergy = minEnergy;
	}
	
	public int getGrassCal() {
		return grassCal;
	}

	public void setGrassCal(int grassCal) {
		this.grassCal = grassCal;
	}
}
