import java.awt.Color;

import uchicago.src.sim.engine.Schedule;
import uchicago.src.sim.engine.SimInit;
import uchicago.src.sim.engine.SimModelImpl;
import uchicago.src.sim.gui.ColorMap;
import uchicago.src.sim.gui.DisplaySurface;
import uchicago.src.sim.gui.Value2DDisplay;

/**
 * Class that implements the simulation model for the rabbits grass simulation.
 * This is the first class which needs to be setup in order to run Repast
 * simulation. It manages the entire RePast environment and the simulation.
 *
 * @author
 */

public class RabbitsGrassSimulationModel extends SimModelImpl {

	private RabbitsGrassSimulationSpace space;

	private Schedule schedule;

	private DisplaySurface displaySurf;

	// DEFAULT VALUES
	private static final int GRID_SIZE = 20, NUM_RABBITS = 5, NUM_GRASS = 10, GROW_RATE = 5, BIRTH_THRESHOLD = 10;

	/*
	 *  PARAMETERS
	 */
	
	//COMPULSORY DO NOT TOUCH
	private int gridSize = GRID_SIZE, numInitRabbits = NUM_RABBITS, numInitGrass = NUM_GRASS,
			grassGrowthRate = GROW_RATE, birthThreshold = BIRTH_THRESHOLD;
	
	//ADDED
	private int minEnergy=10, maxEnergy=30;

	
	/** MAIN: run rabbit grass simulation
	 * @param args args[0] parameters file, args[1] true if batch mode
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

	public void setup() {
		System.out.println("Running setup");

		space = null;

		if (displaySurf != null) {
			displaySurf.dispose();
		}
		displaySurf = null;

		displaySurf = new DisplaySurface(this, "Rabbit Model Window 1");

		registerDisplaySurface("Rabbit Model Window 1", displaySurf);

	}

	public void begin() {
		buildModel();
		buildSchedule();
		buildDisplay();
		
		displaySurf.display();

	}

	public void buildModel() {
		space = new RabbitsGrassSimulationSpace(gridSize);
	}

	public void buildSchedule() {
	}

	public void buildDisplay() {
		
		ColorMap map = new ColorMap();

	    map.mapColor(0, Color.white);
	    map.mapColor(1, Color.green);
	    map.mapColor(2, Color.red);

	    Value2DDisplay displayColor =
	        new Value2DDisplay(space.getGrid(), map);

	    displaySurf.addDisplayable(displayColor, "Color");
	}

	public String[] getInitParam() {
		// Parameters to be set by users via the Repast UI slider bar
		// Do "not" modify the parameters names provided in the skeleton code, you can
		// add more if you want
		String[] params = { "GridSize", "NumInitRabbits", "NumInitGrass", "GrassGrowthRate", "BirthThreshold", 
				/*
				 * ADDED
				 */
				"MaxEnergy","MinEnergy" };
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
	 * @param maxEnergy the maxEnergy to set
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
	 * @param minEnergy the minEnergy to set
	 */
	public void setMinEnergy(int minEnergy) {
		this.minEnergy = minEnergy;
	}
}
