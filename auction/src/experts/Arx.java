package experts;

import java.util.Random;

public class Arx implements Expert {
	
	Random random;
	double a;
	
	public Arx(double a) {
		this.a = a;
		this.random = new Random();
	}

	@Override
	public String name() {
		return "(1+r"+String.format("%1.2f", a)+")*cost";
	}

	@Override
	public Long bid(double marginalCost, double opponentMarginalCost) {
		
		double ratio = 1+ (random.nextDouble() * a);
		// TODO Auto-generated method stub
		return (long) (ratio*marginalCost);
	}

	@Override
	public void update(boolean win, Long opponentBid) {

	}

}
