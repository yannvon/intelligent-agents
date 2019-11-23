package experts;

public class MaxMarginal implements Expert {

	@Override
	public Long bid(double marginalCost, double opponentMarginalCost) {
		return (long) Math.max(marginalCost, opponentMarginalCost);
	}

	@Override
	public void update(boolean win, Long opponentBid) {

	}

}
