package experts;

public class MinMarginal implements Expert {

	@Override
	public Long bid(double marginalCost, double opponentMarginalCost) {
		return (long) Math.min(marginalCost, opponentMarginalCost);
	}

	@Override
	public void update(boolean win, Long opponentBid) {

	}

}
