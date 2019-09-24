import uchicago.src.sim.space.Object2DGrid;

/**
 * Class that implements the simulation space of the rabbits grass simulation.
 * @author 
 */

public class RabbitsGrassSimulationSpace {
	
	private Object2DGrid grid;

	  public RabbitsGrassSimulationSpace(int size){
	    grid = new Object2DGrid(size, size);
	    for(int i = 0; i < size; i++){
	      for(int j = 0; j < size; j++){
	        grid.putObjectAt(i,j,new Integer((i)%3));
	      }
	    }
	  }
	  
	  public Object2DGrid getGrid() {
		  return grid;
	  }

}
