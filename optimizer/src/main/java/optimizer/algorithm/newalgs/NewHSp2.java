package optimizer.algorithm.newalgs;

import core.structs.Tuple;
import optimizer.algorithm.cost.CostEstimatorIface;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.graph.Vertex;
import optimizer.prune.DynamicSkylinePlanPruner;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class NewHSp2 {
    private final Graph rootFlow;
    private final int numPlatforms;
    private final int numSites;
    private final int timeout;
    private final boolean disableStats;
    private final CostEstimatorIface costEstimation;

    // The dynamic skyline pruner maintains a pareto front of all the non dominating solutions
    private final DynamicSkylinePlanPruner pruner;

    // Obsolete plans are those that are removed from the pareto front
    private final Set<String> obsoletePlanSignatures;

    // Examined plans are those that have had their score calculated
    private final Set<String> examinedPlanSignatures;

    // The global queue keeps the plans that need to be expanded
    private final BlockingQueue<Graph> globalQueue;

    private final BlockingQueue<Runnable> workerQueue;

    // Plans that were not pruned at some point are best plan candidates
    private final Set<Graph> bestPlans;

    // Various Executors for the algorithm
    private final ScheduledExecutorService statisticsExecutor;
    private final ScheduledExecutorService timeoutExecutor;
    private final ExecutorService algorithmExecutor;



    private static volatile boolean timeoutReached = false;

    public NewHSp2(Graph rootFlow,
                  int numPlatforms,
                  int numSites,
                  CostEstimatorIface costEstimation,
                  int timeout,
                  boolean disableStats,
                  int numThreads) {
        this.rootFlow = rootFlow;
        this.numPlatforms = numPlatforms;
        this.numSites = numSites;
        this.timeout = timeout;
        this.disableStats = disableStats;
        this.costEstimation = costEstimation;
        this.pruner = new DynamicSkylinePlanPruner(costEstimation);
        this.obsoletePlanSignatures = ConcurrentHashMap.newKeySet();
        this.examinedPlanSignatures = ConcurrentHashMap.newKeySet();
        this.bestPlans = ConcurrentHashMap.newKeySet();
        this.globalQueue = new LinkedBlockingQueue<>();

        this.statisticsExecutor = Executors.newSingleThreadScheduledExecutor();
        this.timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
        this.algorithmExecutor = new ThreadPoolExecutor(
                numThreads,
                numThreads,
                0L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(numThreads),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // Trick to get and initialize the worker queue from inside the executor
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) algorithmExecutor;
        this.workerQueue = tpe.getQueue();

        // run setup() to schedule the time out and statistics threads
        setup();
    }

    /**
     * Method to clean up the executors after the algorithm has terminated.
     */
    public void cleanUp() {
        // Clean up statistics
        // Clean up time out
        // Clean up algorithm executor
    }

    /**
     * Method for scheduling the statistics and time out thread
     */
    private void setup() {
        long startTime = System.currentTimeMillis();
        if (!disableStats) {
            scheduleStatistics(startTime);
        }

        if (timeout != -1) {
            scheduleTimeout();
        }
    }

    /**
     * Method to schedule the thread responsible to time out the algorithm
     */
    private void scheduleTimeout() {
        this.timeoutExecutor.schedule(() -> {
            timeoutReached = true;
        }, timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * Method to schedule the thread that outputs the statistics
     * each 1s
     * @param startTime The time in ms when the method was called
     */
    private void scheduleStatistics(long startTime) {
        this.statisticsExecutor.scheduleAtFixedRate(() -> {
            System.out.print("\rGlobal queue size: " + this.globalQueue.size() +
                    " | " + "Worker queue size: " + this.workerQueue.size() +
                    " | " + "Current skyline size: " + this.pruner.getSkylineSize() +
                    " | " + "Running time: " + (System.currentTimeMillis() - startTime) + " ms");
            System.out.flush();  // Ensure output is flushed to the console
        }, 1000L, 5000L, TimeUnit.MILLISECONDS);
    }

    public Graph execute() {
        this.globalQueue.add(rootFlow);

        while (!globalQueue.isEmpty() && !timeoutReached) {
            ArrayList<Tuple<Integer, Integer>> actions = AlgorithmUtils.getActions(this.numPlatforms, this.numSites);
            Graph parentGraph = globalQueue.poll();
            assert parentGraph != null;
            String parentGraphSignature = parentGraph.getSignatureDashed();

            // Iterate over the vertexIds in order to be able to access the same vertices in each candidateGraph.
            for (Integer vertexId : parentGraph.getVertices().stream().map(Vertex::getOperatorId).collect(Collectors.toList())) {
                if (obsoletePlanSignatures.contains(parentGraphSignature)) {
                    break;
                }

                for (Tuple<Integer, Integer> action : actions) {
                    if (obsoletePlanSignatures.contains(parentGraphSignature)) {
                        break;
                    }

                    // Create the candidate graph as a clone of the parent graph and apply the action
                    Graph candidateGraph = new Graph(parentGraph);
                    int platform = action._1;
                    int site = action._2;
                    Vertex candidateVertex = candidateGraph.getVertex(vertexId);
                    candidateVertex.setPlatform(platform);
                    candidateVertex.setSite(site);

                    // If plan has been examined before, we need to skip it
                    if (examinedPlanSignatures.contains(candidateGraph.getSignatureDashed())) {
                        continue;
                    }

                    this.algorithmExecutor.submit(new HSpTask(candidateGraph));
                }
            }

        }

        return Collections.max(bestPlans, Comparator.comparingInt(Graph::getCost));
    }

    private class HSpTask implements Runnable {
        Graph candidateGraph;

        public HSpTask(Graph candidateGraph) {
            this.candidateGraph = candidateGraph;
        }


        @Override
        public void run() {
            // Calculate the cost of the graph
            candidateGraph.updateCost(costEstimation);

            if (pruner.prune(candidateGraph)) {
                return;
            }

            Set<String> removedFromPareto = pruner.addToSkyline(candidateGraph);
            obsoletePlanSignatures.addAll(removedFromPareto);

            // If any of the removed plans are present in the queue, remove them
            // in order not to be processed
            globalQueue.removeIf(obj -> obsoletePlanSignatures.contains(obj.getSignatureDashed()));

            bestPlans.add(candidateGraph);
        }
    }
}