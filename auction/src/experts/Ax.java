package experts;

public class Ax implements Expert {
	
	double a;
	
	public Ax(double a) {
		a=this.a;
	}

	@Override
	public String name() {
		return String.format("%1.2f", a)+"*cost";
	}

	@Override
	public Long bid(double marginalCost, double opponentMarginalCost) {
		return (long) (a*marginalCost);
	}

	@Override
	public void update(boolean win, Long opponentBid) {
	}

}
