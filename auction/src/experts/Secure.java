package experts;

public class Secure implements Expert {

    Expert e;
    double secureRatio;

    public Secure(Expert e, double startingSecureRatio) {
        this.e = e;
        this.secureRatio = startingSecureRatio;
    }

    @Override
    public Long bid(double marginalCost, double opponentMarginalCost) {
        return (long) (e.bid(marginalCost, opponentMarginalCost) * secureRatio);
    }

    @Override
    public void update(boolean win, Long opponentBid) {
        e.update(win, opponentBid);
        if (win) {
            secureRatio *= 1.05;
        } else {
            secureRatio *= 0.8;
        }

    }

}
