package experts;

public class EstimateOp implements Expert {
    double opRatio, weigthRecent;
    double lastOpCost;

    public EstimateOp(double startingRatio, double weigthRecent) {
        if (weigthRecent < 0 || 1 < weigthRecent) {
            throw new IllegalArgumentException("weight needs to be between 0 and 1");
        }

        this.opRatio = startingRatio;
        this.weigthRecent = weigthRecent;
    }

    @Override
    public Long bid(double marginalCost, double opponentMarginalCost) {
        lastOpCost = opponentMarginalCost;
        return (long) (opRatio * opponentMarginalCost);
    }

    @Override
    public void update(boolean win, Long opponentBid) {
        if (opponentBid != null) {
            double currentRatio = opponentBid / lastOpCost;
            if (currentRatio > 2 * opRatio) {
                currentRatio = 2 * opRatio;
            } else if (currentRatio < 0.5 * opRatio) {
                currentRatio = 0.5 * opRatio;
            }
            opRatio = (weigthRecent * currentRatio) + ((1 - weigthRecent) * opRatio);
        }

    }

}
