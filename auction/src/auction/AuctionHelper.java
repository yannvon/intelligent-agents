package auction;

import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.TaskSet;

import java.util.List;

/**
 * Class that contains helper functions used by all agents implemented.
 */
public class AuctionHelper {

    public static void displayPerformance(String agentName, TaskSet tasks, List<Plan> plans, List<Vehicle> vehicles) {
        double reward = tasks.rewardSum();
        double totalDistance = 0;
        for(
                Plan p : plans) {
            totalDistance += p.totalDistance();
            reward -=(p.totalDistance() * vehicles.get(0).costPerKm());
        }

        System.out.println();
        System.out.println("--- RESULTS : " + agentName + " ---");
        System.out.println("Reward: " + String.format("%6.0f",reward ));
        System.out.println("Number of tasks: " + String.format("%d", tasks.size()));
        System.out.println("Total distance: " + String.format("%6.0f", totalDistance));
        System.out.println();

    }
}
