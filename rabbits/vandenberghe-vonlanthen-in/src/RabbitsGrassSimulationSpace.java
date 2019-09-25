import java.awt.Image;
import java.io.File;

import javax.imageio.ImageIO;

import uchicago.src.sim.gui.Drawable;
import uchicago.src.sim.gui.SimGraphics;
import uchicago.src.sim.space.Discrete2DSpace;
import uchicago.src.sim.space.Object2DGrid;

/**
 * Class that implements the simulation space of the rabbits grass simulation.
 * 
 * @author
 */

public class RabbitsGrassSimulationSpace {

	private Object2DGrid grassSpace;
	private Object2DGrid agentSpace;
	
	private int minGrassCal,maxGrassCal;

	public RabbitsGrassSimulationSpace(int size,int initGrass, int minGrassCal, int maxGrassCal) {

		agentSpace = new Object2DGrid(size, size);
		grassSpace = new Object2DGrid(size, size);
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				grassSpace.putObjectAt(i, j, new Plain(i, j));
			}
		}
		this.minGrassCal = minGrassCal;
		this.maxGrassCal = maxGrassCal;
		
		boolean success = true;
		for(int i =0 ;i<initGrass && success ; i++) {
			success = addGrass();
		}
	}

	public Object2DGrid getGrassSpace() {
		return grassSpace;
	}

	public boolean isCellOccupied(int x, int y) {
		if (agentSpace.getObjectAt(x, y) != null)
			return true;
		return false;
	}
	
	public boolean isCellGrass(int x, int y) {
		if (agentSpace.getObjectAt(x, y) instanceof Grass)
			return true;
		return false;
	}

	
	public boolean addGrass() {
		boolean retVal = false;
		int count = 0;
		int countLimit = 10 * agentSpace.getSizeX() * agentSpace.getSizeY();

		while ((retVal == false) && (count < countLimit)) {
			int x = (int) (Math.random() * (agentSpace.getSizeX()));
			int y = (int) (Math.random() * (agentSpace.getSizeY()));
			if (!isCellGrass(x, y)) {
				grassSpace.putObjectAt(x, y, new Grass(minGrassCal, maxGrassCal, x, y));
				retVal = true;
			}
			count++;
		}

		return retVal;
	}

	public boolean addAgent(RabbitsGrassSimulationAgent agent) {
		boolean retVal = false;
		int count = 0;
		int countLimit = 10 * agentSpace.getSizeX() * agentSpace.getSizeY();

		while ((retVal == false) && (count < countLimit)) {
			int x = (int) (Math.random() * (agentSpace.getSizeX()));
			int y = (int) (Math.random() * (agentSpace.getSizeY()));
			if (isCellOccupied(x, y) == false) {
				agentSpace.putObjectAt(x, y, agent);
				agent.setXY(x, y);
				agent.setSpace(this);
				retVal = true;
			}
			count++;
		}

		return retVal;
	}
	public void removeAgent(int x, int y) {
		agentSpace.putObjectAt(x, y, null);
	}

	public Discrete2DSpace getAgentSpace() {
		return agentSpace;
	}
	
	public static class Plain implements Drawable{

		private int x,y;
		
		private static Image im;
		
		public Plain( int x, int y) {
			if(im ==null) {
				try {
					im = ImageIO.read(new File("images/dirt.png"));
				}catch(Exception e) {
					e.printStackTrace();
				}
			}
			
			this.x = x;
			this.y = y;
		}
		
		@Override
		public void draw(SimGraphics g) {
			g.drawImageToFit(im);
			
		}

		@Override
		public int getX() {
			return x;
		}

		@Override
		public int getY() {
			return y;
		}
		
	}
	
	public static class Grass implements Drawable{
		int calories;
		private int x,y;
		
		private static Image im;
		
		public Grass(int minCalories,int maxCalories, int x, int y) {
			if(im ==null) {
				try {
					im = ImageIO.read(new File("images/grass.png"));
				}catch(Exception e) {
					e.printStackTrace();
				}
			}
			
			calories = (int) ((Math.random() * (maxCalories - minCalories)) + minCalories);
			this.x = x;
			this.y = y;
		}
		
		@Override
		public void draw(SimGraphics g) {
			g.drawImageToFit(im);
			
		}
		@Override
		public int getX() {
			return x;
		}
		@Override
		public int getY() {
			return y;
		}
	}

}
