package experts;

public class AxB extends Ax {
	
	long b;

	public AxB(double a,long b) {
		super(a);
		this.b = b;
	}
	
	@Override
	public String name() {
		return String.format("%1.2f", a)+"*cost +"+String.format("%d", b);
	}
	
	@Override
	public Long bid(double marginalCost, double opponentMarginalCost) {
		return super.bid(marginalCost, opponentMarginalCost) +b;
	}
	
	@Override
	public void update(boolean win, Long opponentBid) {
		super.update(win, opponentBid);
	}

}
