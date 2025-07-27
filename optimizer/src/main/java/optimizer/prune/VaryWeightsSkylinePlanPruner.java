package optimizer.prune;

import core.structs.Tuple;
import optimizer.WeightedFitIoTCostEstimator;
import optimizer.algorithm.cost.PlanCostEstimatorInterface;
import optimizer.algorithm.graph.Graph;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class VaryWeightsSkylinePlanPruner implements GraphPlanPruner{

    // Set to represent the skyline
    private final Set<Tuple<String, Point>> skyline;

    // The cost estimator object
    private final WeightedFitIoTCostEstimator costEstimator;

    // Thread safe variable to count the pruned plans
    private final AtomicLong prunedPlanCount;

    public VaryWeightsSkylinePlanPruner(PlanCostEstimatorInterface costEstimator) {
        this.costEstimator = (WeightedFitIoTCostEstimator) costEstimator;
        this.skyline = ConcurrentHashMap.newKeySet();
        this.prunedPlanCount = new AtomicLong(0);
    }

    private Point makePoint(Graph plan) {
        double[] normalizedCostDimensions = costEstimator.getWeightedNormalizedCostDimensions(plan);
        double throughputNorm = normalizedCostDimensions[0];
        double latencyNorm = normalizedCostDimensions[1];
        double networkUsageNorm = normalizedCostDimensions[2];
        double migrationCostNorm = normalizedCostDimensions[3];
        return new Point(throughputNorm, latencyNorm, networkUsageNorm, migrationCostNorm);
    }

    @Override
    public int getSkylineSize() {
        return this.skyline.size();
    }

    /**
     * Method to add a graph to the skyline. Must be called iff prune() returns false.
     * @param graph - The graph to be inserted into the skyline
     * @return - A list with entries removed from the skyline (dominated by the new point)
     */
    @Override
    public synchronized Set<String> addToSkyline(Graph graph) {
        // First convert the graph into a point
        Point newPoint = makePoint(graph);
        // Initialize the list that will be returned
        Set<String> removed = new HashSet<>();

        // Iterate over the skyline entries and remove those who are dominated by the new point
        // Store the removed entries into the list to be returned
        Iterator<Tuple<String, Point>> skylineIterator = skyline.iterator();
        while(skylineIterator.hasNext()) {
            Tuple<String, Point> skylineEntry = skylineIterator.next();
            if (dominates(newPoint, skylineEntry._2)) {
                skylineIterator.remove();
                removed.add(skylineEntry._1);
            }
        }

        // Finally, add the new point to the skyline and return the list of removed entries.
        this.skyline.add(new Tuple<>(graph.getSignatureDashed(), newPoint));
        return removed;
    }

    // This is meant to be used only in the vary-weights experiment
    // The normalized cost dimensions here can only get better when larger
    // This is why the dominance criterion is >=
    private boolean dominates(Point a, Point b) {
        return a.x() >= b.x() &&
               a.y() >= b.y() &&
               a.z() >= b.z() &&
               a.w() >= b.w();
    }

    /**
     * Method to check if a plan can be pruned
     * @param plan - The plan in question
     * @return - True if the plan can be pruned, false otherwise
     */
    @Override
    public synchronized boolean prune(Graph plan) {
        Point planPoint = makePoint(plan);
        boolean isPruned = this.skyline.stream()
                .anyMatch(skylineElement -> dominates(skylineElement._2, planPoint));
        prunedPlanCount.incrementAndGet();
        return isPruned;
    }

    @SuppressWarnings("unused")
    public void printSkyline() {
        String skylineString = this.skyline.stream()
                .map(point -> point.toString().replaceFirst("Point", ""))
                .collect(Collectors.joining());

        System.out.println(skylineString);
    }
}
