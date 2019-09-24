import java.awt.Color;
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
	
	private static  Image IM;
	

	private static final int ENERGY = 20;

	private int x, y;

	private int energy = ENERGY;
	
	private static int idNumber = 0;
	private final int id;

	public RabbitsGrassSimulationAgent(int minEnergy, int maxEnergy) {
		if(IM == null) {
			try {
				IM = ImageIO.read(new File("images/bunny.jpg"));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		x = -1;
		y = -1;
		energy = (int) ((Math.random() * (maxEnergy - minEnergy)) + minEnergy);
		id = idNumber++;
	}

	public void draw(SimGraphics arg0) {
		arg0.drawFastRoundRect(Color.blue);
		//arg0.drawImage(IM);

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
	
	public void report(){
	    System.out.println(getID() +
	                       " at " +
	                       x + ", " + y +
	                       " has " +
	                       getEnergy() + " energy.");
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

}
