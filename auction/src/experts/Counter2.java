package experts;

public class Counter2 implements Expert {
	private double ratio;

	private double opponentRatio;
	
	private double secureFactor;

	private boolean maximizingReward = false;
	
	private double tax;
	
	private double opMarginalCost;
	
	
	public Counter2(double startingRatio,double startingOpRatio,double startingSecureFactor,double tax) {
		this.secureFactor = startingSecureFactor;
		this.opponentRatio = startingOpRatio;
		this.ratio = startingRatio;
		this.tax = tax;
	}

	@Override
	public Long bid(double marginalCost, double opponentMarginalCost) {
		double bid = (marginalCost + tax) * ratio;

		double opBid = opponentMarginalCost * opponentRatio;
		maximizingReward = bid < opBid * secureFactor;
		if (maximizingReward) {
			bid = opBid * secureFactor;
		}
		opMarginalCost = opponentMarginalCost;

		return (long) Math.round(bid);
	}

	@Override
	public void update(boolean win, Long opBid) {
			if (opBid != null) {
				double simRatio = opBid / opMarginalCost;

				if (simRatio < 3.0 && simRatio > 0) { // TO NOT HAVE TO BIG RATIO FOR SMALL MARGINAL COST
					if (opponentRatio < 0.1) {
						opponentRatio = opBid / opMarginalCost;
					} else {
						opponentRatio = (opponentRatio * 0.6) + (0.4 * (opBid / opMarginalCost));
					}
				}
			}
		
		if (win) {
			ratio += 0.05;
			if (maximizingReward) {
				secureFactor *= 1.1;
			}
		} else {
			// Option 2: Auction was lost

			if (!maximizingReward) {
				ratio -= 0.15;
			} else {
				secureFactor *= 0.85;
			}

		}

		if (ratio < 1.) {
			ratio = 1.;
		}

	}

}
