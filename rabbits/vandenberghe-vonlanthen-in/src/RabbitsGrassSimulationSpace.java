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

	public RabbitsGrassSimulationSpace(int size) {

		agentSpace = new Object2DGrid(size, size);
		grassSpace = new Object2DGrid(size, size);
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				grassSpace.putObjectAt(i, j, new Integer(0));
			}
		}
	}

	public Object2DGrid getGrassSpace() {
		return grassSpace;
	}

	public boolean isCellOccupied(int x, int y) {
		boolean retVal = false;
		if (agentSpace.getObjectAt(x, y) != null)
			retVal = true;
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

}
