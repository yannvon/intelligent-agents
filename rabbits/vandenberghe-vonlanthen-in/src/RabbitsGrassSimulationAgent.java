import java.awt.Image;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import uchicago.src.sim.gui.Drawable;
import uchicago.src.sim.gui.SimGraphics;

/**
 * Class that implements the simulation agent for the rabbits grass simulation.
 * 
 * @author
 */

public class RabbitsGrassSimulationAgent implements Drawable {

	private static Image[] IM;
	private static Image DEAD_IM;
	private static Image REP_IM;

	private static final int ENERGY = 20;
	
	private RabbitsGrassSimulationSpace space;
	
	private int reproduceCount =0;


	private int x, y;

	private int clock;

	private int energy = ENERGY;

	private static int idNumber = 0;
	private final int id;

	public RabbitsGrassSimulationAgent(int minEnergy, int maxEnergy) {
		if (IM == null) {
			try {
				DEAD_IM = ImageIO.read(new File("images/bunnyDead.png"));
				REP_IM = ImageIO.read(new File("images/bunnyHeart.png"));
				IM = new Image[6];
				for (int i = 0; i < 6; i++) {
					IM[i] = ImageIO.read(new File("images/bunny" + i + ".png"));
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		x = -1;
		y = -1;
		clock = 0;
		if(minEnergy>maxEnergy) {
			minEnergy = 1;
		}
		energy = (int) ((Math.random() * (maxEnergy - minEnergy)) + minEnergy);
		id = idNumber++;
	}

	public void draw(SimGraphics arg0) {
		// arg0.drawFastRoundRect(Color.green);
		if(reproduceCount>0) {
			arg0.drawImageToFit(REP_IM);
			reproduceCount--;
		}else if (energy <= 0) {
			arg0.drawImageToFit(DEAD_IM);
		} else {
			arg0.drawImageToFit(IM[(clock++ / 5) % 6]);
		}

	}
	/**
	 * Execute a step of the agent
	 */
	public void step() {
		int dirID = (int) (Math.random()*RabbitsGrassSimulationSpace.Move.values().length);
		
		space.moveIfCan(x, y, RabbitsGrassSimulationSpace.Move.values()[dirID]);
		energy += space.eatGrass(x, y);
		
		
		energy--;
	}
	
	
	public void reproduce(int minEnergy,int maxEnergy) {
		reproduceCount +=1;
		energy = (int) ((Math.random() * (maxEnergy - minEnergy)) + minEnergy);
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public void setXY(int newX, int newY) {
		x = newX;
		y = newY;
	}

	public void report() {
		System.out.println(getID() + " at (" + x + ", " + y + ") has " + getEnergy() + " energy.");
	}

	public String getID() {
		return "Rabbit " + id;
	}

	/**
	 * @return the energy
	 */
	public int getEnergy() {
		return energy;
	}

	
	
	public void setSpace(RabbitsGrassSimulationSpace space) {
		this.space = space;
	}

}
