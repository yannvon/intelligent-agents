package experts;

public class RatioCustom extends Ratio {
	Updater up;

	public RatioCustom(double startingRatio, double tax, double minRatio, Updater up ) {
		super(startingRatio, tax, minRatio);
		this.up=up;
	}
	
	
	@Override
	public void update(boolean win, Long opponentBid) {
		ratio = up.update(ratio, win);
		if(ratio<minRatio) {
			ratio = minRatio;
		}
		

	}

	@FunctionalInterface
	public interface Updater {
	  double update(double ratio, boolean win);
	}
}


