package experts;

public class Average implements Expert {

	private long sum = 0;
	private int count = 0;

	@Override
	public Long bid(double marginalCost, double opponentMarginalCost) {
		if (sum == 0) {
			return (long) marginalCost;
		}
		return sum / count;
	}

	@Override
	public void update(boolean win, Long opponentBid) {
		if (opponentBid != null) {
			count++;
			sum += opponentBid;
		}

	}

}
