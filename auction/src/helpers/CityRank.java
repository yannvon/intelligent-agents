package helpers;

import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.*;


import java.util.*;

/**
 * Page rank algorithm heavily inspired/taken from:
 * https://github.com/jgrapht/jgrapht/blob/master/jgrapht-core/src/main/java/org/jgrapht/alg/scoring/PageRank.java
 */

public class CityRank {

    /**
     * Default number of maximum iterations.
     */
    public static final int MAX_ITERATIONS_DEFAULT = 100;

    /**
     * Default value for the tolerance. The calculation will stop if the difference of PageRank
     * values between iterations change less than this value.
     */
    public static final double TOLERANCE_DEFAULT = 0.0001;

    /**
     * Damping factor default value.
     */
    public static final double DAMPING_FACTOR_DEFAULT = 0.85d;

    private final TaskDistribution distribution;
    private HashSet<City> vertices;

    private Map<City, Double> scores;

    /**
     * Create and execute an instance of PageRank.
     *
     * @param topology the input graph
     */
    public CityRank(Topology topology, TaskDistribution distribution) {
        this(topology, distribution, DAMPING_FACTOR_DEFAULT, MAX_ITERATIONS_DEFAULT, TOLERANCE_DEFAULT);
    }

    /**
     * Create and execute an instance of PageRank.
     *
     * @param topology      the input graph
     * @param dampingFactor the damping factor
     */
    public CityRank(Topology topology, TaskDistribution distribution, double dampingFactor) {
        this(topology, distribution, dampingFactor, MAX_ITERATIONS_DEFAULT, TOLERANCE_DEFAULT);
    }

    /**
     * Create and execute an instance of PageRank.
     *
     * @param topology      the input graph
     * @param dampingFactor the damping factor
     * @param maxIterations the maximum number of iterations to perform
     */
    public CityRank(Topology topology, TaskDistribution distribution, double dampingFactor, int maxIterations) {
        this(topology, distribution, dampingFactor, maxIterations, TOLERANCE_DEFAULT);
    }

    /**
     * Create and execute an instance of PageRank.
     *
     * @param topology      the input graph
     * @param dampingFactor the damping factor
     * @param maxIterations the maximum number of iterations to perform
     * @param tolerance     the calculation will stop if the difference of PageRank values between
     *                      iterations change less than this value
     */
    public CityRank(Topology topology, TaskDistribution distribution, double dampingFactor, int maxIterations, double tolerance) {
        this.distribution = distribution;
        this.scores = new HashMap<>();

        if (maxIterations <= 0) {
            throw new IllegalArgumentException("Maximum iterations must be positive");
        }

        if (dampingFactor < 0.0 || dampingFactor > 1.0) {
            throw new IllegalArgumentException("Damping factor not valid");
        }

        if (tolerance <= 0.0) {
            throw new IllegalArgumentException("Tolerance not valid, must be positive");
        }

        this.vertices = new HashSet<>();
        this.vertices.addAll(topology.cities());
        // No adjacency list needed, since fully connected !

        run(dampingFactor, maxIterations, tolerance);
    }

    public Map<City, Double> getScores() {
        return Collections.unmodifiableMap(scores);
    }

    public Double getVertexScore(City c) {
        if (!vertices.contains(c)) {
            throw new IllegalArgumentException("Cannot return score of unknown vertex");
        }
        return scores.get(c);
    }

    private void run(double dampingFactor, int maxIterations, double tolerance) {
        // initialization
        int totalVertices = vertices.size();
        Map<City, Double> weights;
        weights = new HashMap<>(totalVertices);

        double initScore = 1.0d / totalVertices;
        for (City c : vertices) {
            scores.put(c, initScore);
            double sum = 0;
            for (City c2 : vertices) {
                if (c == c2) continue;

                sum += this.distribution.probability(c, c2);
                weights.put(c, sum);
            }
        }

        // run PageRank
        Map<City, Double> nextScores = new HashMap<>();
        double maxChange = tolerance;

        while (maxIterations > 0 && maxChange >= tolerance) {
            // compute next iteration scores
            double r = 0d;
            for (City c : vertices) {
                r += (1d - dampingFactor) * scores.get(c);
            }
            r /= totalVertices;

            maxChange = 0d;
            for (City c : vertices) {
                double contribution = 0d;

                for (City c2 : vertices) {
                    if (c == c2) continue;

                    contribution +=
                            dampingFactor * scores.get(c2) * distribution.probability(c2, c) / weights.get(c2);
                }

                double vOldValue = scores.get(c);
                double vNewValue = r + contribution;
                maxChange = Math.max(maxChange, Math.abs(vNewValue - vOldValue));
                nextScores.put(c, vNewValue);
            }

            // swap scores
            Map<City, Double> tmp = scores;
            scores = nextScores;
            nextScores = tmp;

            // progress
            maxIterations--;
        }

    }
}
