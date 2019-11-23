package experts;

public interface Expert {

	/**
	 * @return The name of the expert
	 */
	public default String name() {
		return getClass().toString();
	}

	/**
	 * Return the bid choice made by the agent
	 * 
	 * @param marginalCost          the marginal cost for taking the task
	 * @param opponentMarginalCost the simulated marginal cost of the opponent
	 * @return The bid made by this expert
	 */
	public Long bid(double marginalCost, double opponentMarginalCost);

	/**
	 * Update the expert with
	 * 
	 * @param win
	 * @param opponentBid
	 */
	public void update(boolean win, Long opponentBid);

}
