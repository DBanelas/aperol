package optimizer.algorithm.newalgs;

import core.structs.Tuple;
import optimizer.algorithm.cost.*;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.graph.Vertex;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Revised Algorithm NewESQ2 with scheduled statistics output.
 * This version creates the plan space by applying candidate actions to every operator (vertex) of a plan.
 * It uses multiple worker threads, thread-safe state (including atomic counters), and is reentrant.
 * A scheduled thread outputs statistics every second.
 * The algorithm shuts down its executor when computation finishes or when the specified timeout is reached.
 */
public class NewESQ {
    // Instance state (all state is now per-algorithm instance)
    private final BlockingQueue<Graph> planSpaceQueue = new LinkedBlockingQueue<>();
    private final ConcurrentMap<String, Boolean> visitedSignatures = new ConcurrentHashMap<>();
    // Counters for statistics
    private final AtomicInteger cntVisited = new AtomicInteger(0);
    private final AtomicInteger cntExplored = new AtomicInteger(0);
    // For tracking the best plan (based on cost)
    private final AtomicInteger bestCost = new AtomicInteger(0);
    private final AtomicReference<Graph> bestPlan = new AtomicReference<>();
    // A lock to update the best plan atomically (for both best cost and best plan)
    private final Object bestPlanLock = new Object();

    // The list of candidate actions: each tuple represents (candidatePlatform, candidateSite)
    private List<Tuple<Integer, Integer>> actions;
    // The cost estimator to update plan cost
    private PlanCostEstimatorInterface costEstimator;

    /**
     * Creates the plan space by applying candidate actions to every operator (vertex) of a plan.
     *
     * @param flow              The initial plan (Graph)
     * @param costEstimation    The cost estimator to be used
     * @param timeoutMillis     Timeout in milliseconds
     * @return The best plan found (according to the cost estimator)
     */
    public Graph createPlanSpaceWithQueue(Graph flow,
                                          ArrayList<Tuple<Integer, Integer>> actions,
                                          BigInteger possiblePlans,
                                          PlanCostEstimatorInterface costEstimation,
                                          int threads,
                                          int timeoutMillis) {
        this.costEstimator = costEstimation;

        PlanDagStarCostEstimator ce;
        List<Tuple<Integer, Integer>> actionsToPass = actions;
        List<Tuple<Integer, Integer>> sortedActions = new ArrayList<>(actions);
        if (costEstimation instanceof PlanDagStarCostEstimator) {
            ce = (PlanDagStarCostEstimator) costEstimation;
            if (ce.isSimDataset()) {
                actionsToPass = sortedActions;
            }
        } else if (costEstimation instanceof DistributionCostEstimator
                || costEstimation instanceof XGBRegressorCostEstimator
                || costEstimation instanceof SimulationPlanCostEstimator) {
            actionsToPass = sortedActions;
        }

        this.actions = actionsToPass;

        // Initialization: add the initial flow to the plan space and visited list.
        planSpaceQueue.offer(flow);
        visitedSignatures.put(flow.getSignatureDashed(), Boolean.TRUE);
        cntVisited.incrementAndGet();

        // Initialize the best plan. (Higher cost is considered better.)
        flow.updateCost(costEstimator);
        bestCost.set(flow.getCost());
        bestPlan.set(flow);

        // Record the start time.
        long startTimeMillis = System.currentTimeMillis();

        // Create a scheduled executor to output statistics every 1 second.
        ScheduledExecutorService statisticsExecutor = Executors.newSingleThreadScheduledExecutor();
        statisticsExecutor.scheduleAtFixedRate(() -> {
            long elapsed = System.currentTimeMillis() - startTimeMillis;
            System.out.print("\rVisited: " + cntVisited.get() +
                    " | Explored: " + cntExplored.get() +
                    " | Queue Size: " + planSpaceQueue.size() +
                    " | Best Cost: " + bestCost.get() +
                    " | Running Time: " + elapsed + " ms");
            System.out.flush();
        }, 1000L, 1000L, TimeUnit.MILLISECONDS);

        // Create an executor service with a fixed number of worker threads.
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        // Submit worker tasks.
        // Each worker will poll from the queue and generate new plans.
        for (int i = 0; i < threads; i++) {
            executor.submit(this::workerTask);
        }

        // Wait for completion up to timeoutMillis.
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
                System.out.println("\nAlgorithm timed out.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            System.out.println("\nExecutor service awaitTermination was interrupted.");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Shutdown the statistics executor.
        statisticsExecutor.shutdownNow();

        long elapsedMillis = System.currentTimeMillis() - startTimeMillis;

        // Print final statistics.
        System.out.println("\nTime: " + elapsedMillis + " ms, Visited: " + cntVisited.get() +
                ", Explored: " + cntExplored.get());
        System.out.println("Best cost: " + bestCost.get());
        System.out.println("Best plan signature: " + bestPlan.get().getSignature());
        System.out.println("Best plan:\n" + bestPlan.get());

        return bestPlan.get();
    }

    /**
     * Worker task that repeatedly polls the plan space queue and applies all candidate actions to each vertex.
     */
    private void workerTask() {
        try {
            // Use a poll timeout so that the thread doesn't block indefinitely.
            while (!Thread.currentThread().isInterrupted()) {
                Graph currentPlan = planSpaceQueue.poll(100, TimeUnit.MILLISECONDS);
                if (currentPlan == null) {
                    // If the queue is empty, assume work is done.
                    break;
                }

                // Check whether this plan has been examined before. If yes, do not examine it again
                String currentPlanSignature = currentPlan.getSignatureDashed();
//                if (visitedSignatures.containsKey(currentPlanSignature)) {
//                    continue;
//                }
                processPlan(currentPlan);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore interrupt status.
        }
    }

    /**
     * Processes a single plan by applying every candidate action to each vertex.
     *
     * @param plan The plan (Graph) to process.
     */
    private void processPlan(Graph plan) {
        List<Vertex> vertices = plan.getVertices();
        for (int vertexIndex = 0; vertexIndex < vertices.size(); vertexIndex++) {
            // For each candidate action
            for (Tuple<Integer, Integer> action : actions) {

                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                // Create a new plan copy and apply the candidate action.
                Graph newPlan = new Graph(plan);
                Vertex v = newPlan.getVertices().get(vertexIndex);
                v.setPlatform(action._1);
                v.setSite(action._2);
//                newPlan.updateVertex(v);

                // Compute the plan's signature after applying the changes.
                String newSignature = newPlan.getSignature();

                // Only process the plan further if it hasn't been visited.
                if (visitedSignatures.putIfAbsent(newSignature, Boolean.TRUE) == null) {
                    // Update cost.
                    newPlan.updateCost(costEstimator);
                    int newCost = newPlan.getCost();

                    // Update the best plan if the new plan's cost is better.
                    if (costEstimator instanceof PlanDagStarCostEstimator) {
                        if (newCost < bestCost.get()) {
                            bestCost.set(newCost);
                            bestPlan.set(newPlan);
                        }
                    } else {
                        if (newCost > bestCost.get()) {
                            bestCost.set(newCost);
                            bestPlan.set(newPlan);
                        }
                    }

                    // Add new plan to the queue.
                    planSpaceQueue.offer(newPlan);
                    cntVisited.incrementAndGet();
                    cntExplored.incrementAndGet();
                }
            }
        }
    }
}
