import java.awt.Color;
import java.awt.Image;

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

	private int grassCal;

	public RabbitsGrassSimulationSpace(int size, int initGrass, int grassCal) {

		agentSpace = new Object2DGrid(size, size);
		grassSpace = new Object2DGrid(size, size);
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				grassSpace.putObjectAt(i, j, new Cell(i, j));
			}
		}
		this.grassCal = grassCal;

		boolean success = true;
		for (int i = 0; i < initGrass && success; i++) {
			success = addGrass();
		}
	}

	/*
	 * ============================================================================
	 * ----------------------- GRASS RELATED METHODS ------------------------------
	 * ============================================================================
	 * 
	 */

	public Object2DGrid getGrassSpace() {
		return grassSpace;
	}

	public boolean hasCellGrass(int x, int y) {
		return ((Cell) grassSpace.getObjectAt(x, y)).type == Cell.Type.GRASS;
	}

	public boolean addGrass() {
		boolean retVal = false;
		int count = 0;
		int countLimit = 10 * agentSpace.getSizeX() * agentSpace.getSizeY();

		while ((retVal == false) && (count < countLimit)) {
			int x = (int) (Math.random() * (agentSpace.getSizeX()));
			int y = (int) (Math.random() * (agentSpace.getSizeY()));
			Cell cell = (Cell) grassSpace.getObjectAt(x, y);
			if (cell.type == Cell.Type.GROUND) {
				cell.growGrass(grassCal);
				;
				retVal = true;
			}
			count++;
		}

		return retVal;
	}

	public int eatGrass(int x, int y) {
		Cell cell = (Cell) grassSpace.getObjectAt(x, y);
		return cell.cutGrass();
	}

	public int countGrass() {
		int c = 0;
		for (int i = 0; i < grassSpace.getSizeX(); i++) {
			for (int j = 0; j < grassSpace.getSizeY(); j++) {
				if(hasCellGrass(i, j)) {
					c++;
				}

			}
		}
		return c;
	}

	/*
	 * ============================================================================
	 * ----------------------- AGENT RELATED METHODS ------------------------------
	 * ============================================================================
	 * 
	 */
	public enum Move {
		UP, RIGHT, DOWN, LEFT
	}

	public Discrete2DSpace getAgentSpace() {
		return agentSpace;
	}

	public boolean isCellOccupied(int x, int y) {
		if (agentSpace.getObjectAt(x, y) != null)
			return true;
		return false;
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

	public boolean moveIfCan(int x, int y, Move direction) {
		if (isCellOccupied(x, y)) {
			int nextX = x, nextY = y;
			switch (direction) {
			case UP:
				nextY = (y + 1 + agentSpace.getSizeY()) % agentSpace.getSizeY();
				break;
			case DOWN:
				nextY = (agentSpace.getSizeY() + y - 1) % agentSpace.getSizeY();
				break;
			case LEFT:
				nextX = (agentSpace.getSizeX() + x - 1) % agentSpace.getSizeX();
				break;
			case RIGHT:
				nextX = (agentSpace.getSizeX() + x + 1) % agentSpace.getSizeX();
				break;
			}

			if (!isCellOccupied(nextX, nextY)) {
				RabbitsGrassSimulationAgent ag = (RabbitsGrassSimulationAgent) agentSpace.getObjectAt(x, y);
				agentSpace.putObjectAt(x, y, null);
				agentSpace.putObjectAt(nextX, nextY, ag);
				ag.setXY(nextX, nextY);
				return true;
			}

		}

		return false;
	}

	public static class Cell implements Drawable {
		public enum Type {
			GROUND, GRASS
		}

		private int calories;
		private int x, y;
		private Type type;

		private static Image imGrass, imGround;

		public Cell(int x, int y) {
			/*if (imGrass == null || imGround == null) {
				try {
					imGrass = ImageIO.read(getClass().getResource("grass.png"));
					imGround = ImageIO.read(getClass().getResource("dirt.png"));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}*/

			calories = 0;
			this.x = x;
			this.y = y;
			type = Type.GROUND;
		}

		public void growGrass(int calories) {
			this.calories = calories;
			type = Type.GRASS;
		}

		public int cutGrass() {
			int temp = calories;
			calories = 0;
			type = Type.GROUND;
			return temp;
		}

		@Override
		public void draw(SimGraphics g) {
			switch (type) {
			case GROUND:
				g.drawRect(Color.WHITE);
				//g.drawImageToFit(imGround);
				break;
			case GRASS:
				g.drawRect(Color.GREEN);
				//g.drawImageToFit(imGrass);
			}

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
