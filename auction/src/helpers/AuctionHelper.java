package helpers;

import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.TaskSet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.List;

/**
 * Class that contains helper functions used by all agents implemented.
 */
public class AuctionHelper {

	public static void displayAndLogPerformance(String agentName, TaskSet tasks, List<Plan> plans,
												List<Vehicle> vehicles, Logger log) {
		double reward = tasks.rewardSum();
		double totalDistance = 0;
		for (Plan p : plans) {
			totalDistance += p.totalDistance();
			reward -= (p.totalDistance() * vehicles.get(0).costPerKm());
		}

		// Log final reward after planning again
		if (log != null) {
			log.logToFile(-1, reward);
		}

		System.out.println();
		System.out.println("--- RESULTS : " + agentName + " ---");
		System.out.println("Reward: " + String.format("%6.0f", reward));
		System.out.println("Number of tasks: " + String.format("%d", tasks.size()));
		System.out.println("Total distance: " + String.format("%6.0f", totalDistance));
		System.out.println();

	}

	/**
	 * For a random variable X whose values are distributed according to Poisson distribution with mean m,
	 * this method returns P(X <= x).
	 *
	 * @param m mean
	 * @param x rv
	 */
	public static double cumulativePoissonDistribution(double m, int x) {
		double cumulative = 0;
		for (int i = 0; i <= x; i++) {
			cumulative += poisson(m, i);
		}

		return cumulative;
	}

	/**
	 * For a random variable X whose values are distributed according to the Possion distribution,
	 * this method returns P(X = x).
	 * @param m
	 * @param y
	 * @return
	 */
	private static double poisson(double m, int y) {
		// Compute factorial
		long fact = 1;
		for (int i = 2; i <= y; i++) {
			fact = fact * i;
		}
		return Math.exp(-m) * Math.pow(m, y) / fact;
	}
}
