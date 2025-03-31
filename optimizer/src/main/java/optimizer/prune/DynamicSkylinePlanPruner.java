package optimizer.prune;

import core.structs.Tuple;
import core.structs.rtree.geometry.Point;
import optimizer.algorithm.cost.CostEstimatorIface;
import optimizer.algorithm.cost.DagStarCostEstimator;
import optimizer.algorithm.graph.Graph;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Simple method to calculate and dynamically maintain a skyline of 2D points
 * in a thread safe manner.
 * The Point class is borrowed from the core.structs.tree.geometry.Point class.
 * The skyline is represented as a Set (ConcurrentHashMap.newKeySet() for thread safety)
 * The main method is the prune() method in order to be used in the HSp algorithm.
 * First, a Point(x, y) is created out of the incoming Graph object.
 * Then the newly created point is checked against the current skyline in order to see if it
 * dominated by any of the points in the skyline.
 * This is accomplished with the use of the dominates() method that is custom-made for our use case.
 * If a plan cannot be added to the skyline, it is simply ignored.
 * If a plan CAN be added to the skyline, it is added after checking if it dominates any of
 * the existing plans in the skyline. Those plans are eliminated from the skyline.
 */
public class DynamicSkylinePlanPruner implements GraphPlanPruner {

    // Set to represent the skyline
    private final Set<Tuple<String, Point>> skyline;

    // The cost estimator object
    private final CostEstimatorIface costEstimator;

    // Thread safe variable to count the pruned plans
    private final AtomicLong prunedPlanCount;

    public DynamicSkylinePlanPruner(CostEstimatorIface costEstimator) {
        this.skyline = ConcurrentHashMap.newKeySet();
        this.prunedPlanCount = new AtomicLong(0);
        this.costEstimator = costEstimator;
    }

    private Point makePoint(Graph plan) {
        int migrationCost = 0;
        int metricsScore = 0;
        try {
            migrationCost = this.costEstimator.getMigrationCost(plan);
            metricsScore = this.costEstimator.getRealCost(plan);
        } catch (RuntimeException ignored) {

        }
        return new Point(migrationCost, metricsScore);
    }

    public int getSkylineSize() {
        return this.skyline.size();
    }

    /**
     * Method to add a graph to the skyline. Must be called iff prune() returns false.
     * @param graph - The graph to be inserted into the skyline
     * @return - A list with entries removed from the skyline (dominated by the new point)
     */
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

        // Finally add the new point to the skyline and return the list of removed entries.
        this.skyline.add(new Tuple<>(graph.getSignatureDashed(), newPoint));
        return removed;
    }

    // For the x coordinate the predicate for domination is: x >= x'
    // For the y coordinate the predicate for domination is: y <= y'
    private boolean dominates(Point a, Point b) {
        if (costEstimator instanceof DagStarCostEstimator) {
            return a.x() <= b.x() && a.y() <= b.y(); // FOR DAG* EXPERIMENTS TODO: CHANGE
        } else {
            return a.x() <= b.x() && a.y() >= b.y(); // FOR DAG* EXPERIMENTS TODO: CHANGE
        }
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

    public long getPrunedPlanCount() {
        return prunedPlanCount.get();
    }

    @Override
    public void pruneList(Collection<Graph> collection) {
        List<Graph> toRemove = new ArrayList<>();
        for (Graph plan : collection) {
            if (prune(plan)) {
                toRemove.add(plan);
            }
        }
        collection.removeAll(toRemove);
    }

    @Override
    public int getPrunedPlans() {
        return 0;
    }
}
