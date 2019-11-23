package experts;

public class SecureFix implements Expert {
	
	private final Expert e;
	private final double secureRatio;
	
	public SecureFix(Expert e,double secureRatio) {
		this.e = e;
		this.secureRatio = secureRatio;
	}

	@Override
	public Long bid(double marginalCost, double opponentMarginalCost) {
		return (long) (e.bid(marginalCost, opponentMarginalCost) * secureRatio);
	}

	@Override
	public void update(boolean win, Long opponentBid) {
		e.update(win, opponentBid);

	}

}
