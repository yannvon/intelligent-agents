package experts;

/**
 * Very conservative expert that only bids slightly above marginal cost.
 */
public class Conservative implements Expert {
	double c;

	public Conservative() { }
	
	@Override
	public String name() {
		return "Conservative: bid marginal cost +" + String.format("%1.2f", c);
	}
	
	@Override
	public Long bid(double marginalCost, double opponentMarginalCost) {
		return Math.round(marginalCost + c);
	}
	
	@Override
	public void update(boolean win, Long opponentBid) { }

}
