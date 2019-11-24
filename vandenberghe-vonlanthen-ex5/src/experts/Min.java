package experts;

public class Min implements Expert {

    private Expert e1, e2;


    public Min(Expert e1, Expert e2) {
        this.e1 = e1;
        this.e2 = e2;
    }


    @Override
    public Long bid(double marginalCost, double opponentMarginalCost) {
        Long b1 = e1.bid(marginalCost, opponentMarginalCost);
        Long b2 = e2.bid(marginalCost, opponentMarginalCost);
        return Math.min(b1, b2);
    }

    @Override
    public void update(boolean win, Long opponentBid) {
        e1.update(win, opponentBid);
        e2.update(win, opponentBid);

    }

}
