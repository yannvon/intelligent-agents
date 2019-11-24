package experts;

/**
 * This expert inherits from the ratio experts, i.e.:
 * It uses a ratio to increases its profit margins if it is winning.
 * When using minRation = 1, it will never loose money.
 *
 * Additionally the way the ratio is updated is fully customizable with an anonymous function.
 */
public class RatioCustom extends Ratio {
    private Updater up;

    public RatioCustom(double startingRatio, double tax, double minRatio, Updater up) {
        super(startingRatio, tax, minRatio);
        this.up = up;
    }

    @Override
    public void update(boolean win, Long opponentBid) {
        ratio = up.update(ratio, win);
        if (ratio < minRatio) {
            ratio = minRatio;
        }
    }

    @FunctionalInterface
    public interface Updater {
        double update(double ratio, boolean win);
    }
}


