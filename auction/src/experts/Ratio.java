package experts;

public class Ratio implements Expert {
	
	double ratio ;
	double tax;
	double minRatio;
	
	public Ratio(double startingRatio,double tax,double minRatio) {
		ratio = startingRatio;
		this.tax = tax;
	}

	@Override
	public Long bid(double marginalCost, double opponentMarginalCost) {
		return (long) ((marginalCost+tax) *ratio);
	}

	@Override
	public void update(boolean win, Long opponentBid) {
		if(win) {
			ratio += 0.05;
		}else {
			ratio -= 0.15;
		}
		if(ratio<minRatio) {
			ratio = minRatio;
		}
		

	}

}
