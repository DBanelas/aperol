package optimizer.algorithm.newalgs;

import core.structs.Tuple;
import optimizer.algorithm.cost.CostEstimatorIface;
import optimizer.algorithm.cost.DagStarCostEstimator;
import optimizer.algorithm.cost.XGBRegressorCostEstimator;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.graph.Vertex;
import optimizer.prune.DynamicSkylinePlanPruner;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class NewHSp {
    private final int maxPlans;

    private static final Object POISON_PILL = new Object();
    private CountDownLatch latch;
    private ArrayList<HSpTask> tasks;
    private Set<Graph> planGraph;
    private ExecutorService executorService;
    private AtomicInteger activeTasks;

    private final Graph rootFlow;
    private final int numPlatforms;
    private final int numSites;
    private final int timeout;
    private final int numThreads;
    private final boolean disableStats;
    private final CostEstimatorIface costEstimation;
    private final Set<Integer> cloudOnlyOperatorIds;


    private final String modelDirectory;
    private final String modelNetwork;
    private final String modelWorkflow;

    // Variables used in the statistics printing
    private static ScheduledExecutorService statisticsExecutor;
    private final static AtomicInteger plansGeneratedAt = new AtomicInteger(0);
    private final static AtomicInteger plansPrunedAt = new AtomicInteger(0);

    public NewHSp(Graph rootFlow,
                  int numPlatforms,
                  int numSites,
                  Set<Integer> cloudOnlyOperatorIds,
                  CostEstimatorIface costEstimation,
                  int timeout,
                  boolean disableStats,
                  int numThreads,
                  int sampleSize) {
        this.maxPlans = sampleSize;

        this.rootFlow = rootFlow;
        this.numPlatforms = numPlatforms;
        this.numSites = numSites;
        this.cloudOnlyOperatorIds = cloudOnlyOperatorIds;
        this.timeout = timeout;
        this.numThreads = numThreads;
        this.disableStats = disableStats;
        this.costEstimation = costEstimation;
        this.modelDirectory = null;
        this.modelNetwork = null;
        this.modelWorkflow = null;
    }

    public NewHSp(Graph rootFlow,
                  int numPlatforms,
                  int numSites,
                  Set<Integer> cloudOnlyOperatorIds,
                  CostEstimatorIface costEstimation,
                  int timeout,
                  boolean disableStats,
                  int numThreads,
                  int sampleSize,
                  String modelDirectory,
                  String modelNetwork,
                  String modelWorkflow) {
        this.maxPlans = sampleSize;

        this.rootFlow = rootFlow;
        this.numPlatforms = numPlatforms;
        this.numSites = numSites;
        this.cloudOnlyOperatorIds = cloudOnlyOperatorIds;
        this.timeout = timeout;
        this.numThreads = numThreads;
        this.disableStats = disableStats;
        this.costEstimation = costEstimation;
        this.modelDirectory = modelDirectory;
        this.modelNetwork = modelNetwork;
        this.modelWorkflow = modelWorkflow;
    }

    /**
     * Method to clean up the executors after the algorithm has terminated.
     */
    public void cleanUp() {
        statisticsExecutor.shutdown();
        try {
            executorService.shutdown();
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                List<Runnable> remainingTasks = executorService.shutdownNow();
                System.out.println("Remaining tasks: " + remainingTasks.size());
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        plansGeneratedAt.set(0);
        plansPrunedAt.set(0);
    }

    /**
     * Method to schedule the thread that outputs the statistics
     * each 1s
     * @param startTime The time in ms when the method was called
     */
    private static void scheduleStatistics(long startTime,
                                           BlockingQueue<Object> planQueue,
                                           DynamicSkylinePlanPruner pruner,
                                           AtomicInteger activeTasks,
                                           CountDownLatch latch) {
        statisticsExecutor.scheduleAtFixedRate(() -> {
            System.out.print("\rPlans generated: " + plansGeneratedAt +
                    " | " + "Plans pruned: " + plansPrunedAt +
                    " | " + "Current queue size: " + planQueue.size() +
                    " | " + "Current skyline size: " + pruner.getSkylineSize() +
                    " | " + "Active tasks: " +  activeTasks.get() +
                    " | " + "Latch count: " +  latch.getCount() +
                    " | " + "Running time: " + (System.currentTimeMillis() - startTime) + " ms");
            System.out.flush();  // Ensure output is flushed to the console
        }, 1000L, 5000L, TimeUnit.MILLISECONDS);
    }

    public void setup() {
        ArrayList<Tuple<Integer, Integer>> actions = AlgorithmUtils.getActions(numPlatforms, numSites);
        this.planGraph = ConcurrentHashMap.newKeySet();
        this.planGraph.add(this.rootFlow);
        this.executorService = Executors.newFixedThreadPool(this.numThreads);
        statisticsExecutor = Executors.newSingleThreadScheduledExecutor();
        this.latch = new CountDownLatch(this.numThreads);
        this.tasks = new ArrayList<>();
        this.activeTasks = new AtomicInteger(0);

        // Queue to store the plans that need to be expanded
        BlockingQueue<Object> planQueue = new LinkedBlockingQueue<>();
        boolean added = planQueue.offer(this.rootFlow);
        if (!added) {
            throw new RuntimeException("Could not add root plan to the queue");
        }
        long startTime = System.currentTimeMillis();

        Set<String> globalSignatureSet = ConcurrentHashMap.newKeySet();
        globalSignatureSet.add(this.rootFlow.getSignatureDashed());

        Set<String> removed = ConcurrentHashMap.newKeySet();
        DynamicSkylinePlanPruner pruner = new DynamicSkylinePlanPruner(this.costEstimation);
        System.out.println("Initial skyline size: " + pruner.getSkylineSize());
        // Scheduling the statistics thread
        scheduleStatistics(startTime, planQueue, pruner, activeTasks, latch);
        for (int i = 0; i < this.numThreads; i++) {

            if (this.modelDirectory == null) {
                HSpTask task = new HSpTask(
                        i, cloudOnlyOperatorIds, planGraph,
                        planQueue, globalSignatureSet, removed,
                        actions, this.costEstimation, pruner,
                        latch, maxPlans
                );
                tasks.add(task);
            } else {
                HSpTask task = new HSpTask(
                        i, cloudOnlyOperatorIds, planGraph,
                        planQueue, globalSignatureSet, removed,
                        actions, pruner, latch, modelDirectory,
                        modelNetwork, modelWorkflow, maxPlans
                );
                tasks.add(task);
            }
        }
    }

    public Graph execute() {
        try {
            for (Future<String> task : executorService.invokeAll(tasks, timeout, TimeUnit.MILLISECONDS)) {
                try {
                    System.out.printf("Task completed successfully with [%s]%n", task.get());
                } catch (InterruptedException e) {
                    System.out.println("Task was interrupted.");
                } catch (CancellationException e) {
                    System.out.println("Task was cancelled.");
                } catch (ExecutionException e) {
                    System.out.printf("Executor encountered the following error: [%s]%n", e);
                }
            }
        } catch (InterruptedException e) {
            System.out.println("Executor was interrupted.");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            System.out.println("Latch was interrupted.");
        }

        System.out.println("Total plans generated: " + plansGeneratedAt.get());

        Graph bestPlan;
        if (costEstimation instanceof DagStarCostEstimator) {
            bestPlan =  Collections.min(planGraph, Comparator.comparingInt(Graph::getCost));
            System.out.println("Min plan cost: " + bestPlan.getCost());
        } else {
            bestPlan =  Collections.max(planGraph, Comparator.comparingInt(Graph::getCost));
            System.out.println("Min plan cost: " + (bestPlan.getCost() - rootFlow.getCost()));
        }
        System.out.println("Min plan signature: " + bestPlan.getSignatureDashed());
        System.out.println("Min plan graph: \n" + bestPlan);

//        System.out.println("-------------- PLAN METRICS -----------------");
//        System.out.println();
//        System.out.println("--------- ROOT PLAN ---------");
//        ((PlanCostEstimator) costEstimation).printMetricsForFlow(this.rootFlow);
//        System.out.println();
//        System.out.println("--------- BEST PLAN ---------");
//        ((PlanCostEstimator) costEstimation).printMetricsForFlow(bestPlan);

        return bestPlan;
    }

    private class HSpTask implements Callable<String> {
        private final int maxPlans; // Optional if you need to pass it separately
        //Used to stop the worker when the queue empties
        private final int workerID;
        private final Set<Graph> planGraph;
        private final Set<String> globalSignatureSet;
        private final ArrayList<Tuple<Integer, Integer>> actions;
        private final Set<Integer> cloudOnlyOperatorIds;
        private final CostEstimatorIface costEstimator;
        private final DynamicSkylinePlanPruner planPruner;
        private final CountDownLatch latch;
        private final BlockingQueue<Object> planQueue;
        private final Set<String> removed;

        private final String modelDirectory;
        private final String modelNetwork;
        private final String modelWorkflow;

        //Stats for this worker
        private int plansExplored;
        private final int graphCollisions;
        private int setCollisions;

        public HSpTask(int workerID,
                       Set<Integer> cloudOnlyOperatorIds,
                       Set<Graph> planGraph,
                       BlockingQueue<Object> planQueue,
                       Set<String> globalSignatureSet,
                       Set<String> removed,
                       ArrayList<Tuple<Integer, Integer>> actions,
                       CostEstimatorIface costEstimator,
                       DynamicSkylinePlanPruner planPruner,
                       CountDownLatch latch,
                       int maxPlans) {
            this.maxPlans = maxPlans;

            this.workerID = workerID;
            this.cloudOnlyOperatorIds = cloudOnlyOperatorIds;
            this.planGraph = planGraph;
            this.planQueue = planQueue;
            this.globalSignatureSet = globalSignatureSet;
            this.removed = removed;
            this.actions = actions;
            this.costEstimator = costEstimator;
            this.planPruner = planPruner;
            this.latch = latch;
            this.plansExplored = 0;
            this.graphCollisions = 0;
            this.setCollisions = 0;

            this.modelDirectory = null;
            this.modelNetwork = null;
            this.modelWorkflow = null;
        }

        public HSpTask(int workerID,
                       Set<Integer> cloudOnlyOperatorIds,
                       Set<Graph> planGraph,
                       BlockingQueue<Object> planQueue,
                       Set<String> globalSignatureSet,
                       Set<String> removed,
                       ArrayList<Tuple<Integer, Integer>> actions,
                       DynamicSkylinePlanPruner planPruner,
                       CountDownLatch latch,
                       String modelDirectory,
                       String modelNetwork,
                       String modelWorkflow,
                       int maxPlans) {
            this.maxPlans = maxPlans;

            this.workerID = workerID;
            this.cloudOnlyOperatorIds = cloudOnlyOperatorIds;
            this.planGraph = planGraph;
            this.planQueue = planQueue;
            this.globalSignatureSet = globalSignatureSet;
            this.removed = removed;
            this.actions = actions;
            this.planPruner = planPruner;
            this.latch = latch;
            this.plansExplored = 0;
            this.graphCollisions = 0;
            this.setCollisions = 0;

            this.modelDirectory = modelDirectory;
            this.modelNetwork = modelNetwork;
            this.modelWorkflow = modelWorkflow;

            XGBRegressorCostEstimator xgbCostEstimator = (XGBRegressorCostEstimator) costEstimation;
            xgbCostEstimator.setModelDirectory(this.modelDirectory);
            xgbCostEstimator.setModelNetwork(this.modelNetwork);
            xgbCostEstimator.setModelWorkflow(this.modelWorkflow);
            try {
                xgbCostEstimator.loadModels();
            } catch (Exception e) {
                System.out.println("Error loading XGB models! Exiting...");
                System.exit(1);
            }

            this.costEstimator = xgbCostEstimator;
        }

        @Override
        public String call() {
            final Instant startInstant = Instant.now();

            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object dequeuedObject;
                    try {
                        dequeuedObject = planQueue.poll(2, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    if (shouldTerminate(dequeuedObject)) {
                        break;
                    }

                    final Graph parent = (Graph) dequeuedObject;
                    activeTasks.incrementAndGet();

                    try {
                        assert parent != null;
                        processParentGraph(parent);
                    } finally {
                        activeTasks.decrementAndGet();
                    }
                }

                logWorkerCompletion(startInstant);

                return String.format("ID=%d", this.workerID);

            } finally {
                latch.countDown();
            }
        }

        /**
         * Method to determine whether a worker should terminate its processing.
         * Checks for poison pill.
         * Checks for null object and 0 active tasks.
         * @param dequeuedObject    The dequeued object
         * @return                  True if the worker needs to be terminated, false, otherwise
         */
        private boolean shouldTerminate(Object dequeuedObject) {
            // If the dequeuedObject is null, it means that the timeout was reached and the worker
            // did not manage to find a plan in the queue, therefore needs terminating
            if (dequeuedObject == null) {
                if (activeTasks.get() == 0) {
                    if (!planQueue.offer(POISON_PILL)) {
                        throw new RuntimeException("Could not add POISON to the queue!");
                    }
                    return true;
                }
                return false;
            }

            // If dequeuedObject is poison, the worker needs to die
            if (dequeuedObject == POISON_PILL) {
                if (!planQueue.offer(POISON_PILL)) {
                    throw new RuntimeException("Could not add POISON to the queue!");
                }
                return true;
            }
            return false;
        }

        /**
         * Main processing method. Iterates over the actions and applies each action
         * to the parent graph.
         * @param parent    The parent graph, which the actions will be applied to
         */
        private void processParentGraph(Graph parent) {
            boolean parentFoundInRemoved = false;

            for (Vertex parentVertex : parent.getVertices()) {
                if (parentFoundInRemoved) {
                    break;
                }

                if (Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                    return;
                }

                // Skip the operators that can only be placed in the cloud
                if (this.cloudOnlyOperatorIds.contains(parentVertex.getOperatorId())) continue;

                for (Tuple<Integer, Integer> action : this.actions) {
                    if (this.removed.contains(parent.getSignatureDashed())) {
                        parentFoundInRemoved = true;
                        break;
                    }

                    if (Thread.currentThread().isInterrupted()) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    processAction(parent, parentVertex, action);
                }
            }
        }

        /**
         * Method to process a given action along with a given parent graph and vertex.
         * Performs several checks in order to determine whether the resulting plan has been
         * examined before.
         * Checks if the plan belongs to the pareto front. If it can't, it is pruned.
         * @param parent          The parent graph
         * @param parentVertex    The parent vertex
         * @param action          The given action
         */
        private void processAction(Graph parent, Vertex parentVertex, Tuple<Integer, Integer> action) {
            try {
                Graph candidateGraph = createCandidateGraph(parent, parentVertex, action);
                String candidateSignature = candidateGraph.getSignatureDashed();

                if (!globalSignatureSet.add(candidateSignature)) {
                    this.setCollisions++;
                    return;
                }

                candidateGraph.updateCost(costEstimator);
                plansGeneratedAt.incrementAndGet();

                if (planPruner.prune(candidateGraph)) {
                    plansPrunedAt.incrementAndGet();
                    return;
                }

                handleNewCandidate(candidateGraph);

            } catch (Exception e) {
                // Log the exception and continue processing
                logException(e);
            }
        }

        /**
         * Method to create the candidate graph out of the parent graph, the parent vertex and
         * a given action. Applies the action to the given vertex and returns the new graph.
         * @param parent         The parent graph
         * @param parentVertex   The parent vertex
         * @param action         The given action
         * @return               The newly created candidate graph
         */
        private Graph createCandidateGraph(Graph parent, Vertex parentVertex, Tuple<Integer, Integer> action) {
            // Unpack the (platform, site) tuple
            int candidatePlatform = action._1;
            int candidateSite = action._2;

            // Create a (deep) copy of the parent graph and get the needed vertex by its id
            Graph candidateGraph = new Graph(parent);
            Vertex candidateVertex = candidateGraph.getVertex(parentVertex.getOperatorId());

            // Apply the action
            candidateVertex.setPlatform(candidatePlatform);
            candidateVertex.setSite(candidateSite);

            // Return the result
            return candidateGraph;
        }

        /**
         * Method to finish the processing of the candidate plan.
         * Inserts it to the Pareto front and removes those that are dominated by it.
         * Adds it to the planGraph with the possible solutions.
         * Adds it back to the queue for further expansion
         * @param candidateGraph    The candidate graph
         */
        private void handleNewCandidate(Graph candidateGraph) {
            // Add the plan to the skyline and remove those that are dominated
            Set<String> removed = planPruner.addToSkyline(candidateGraph);
            this.removed.addAll(removed);

            // If any of the removed plans are present in the queue, remove them
            // in order not to be processed
            planQueue.removeIf(obj -> {
                if (obj instanceof Graph) {
                    Graph graph = (Graph) obj;
                    return removed.contains(graph.getSignatureDashed());
                }
                return false;
            });

            // Add the plan to the possible solutions
            planGraph.add(candidateGraph);
            this.plansExplored++;

            // Add the plan back to the queue
            if (!planQueue.offer(candidateGraph)) {
                throw new IllegalStateException(
                        String.format("Worker %s failed to offer candidate plan to queue", workerID));
            }
        }

        /**
         * Method to log the completion of the processing of a worker
         * @param startInstant      The instant this worker started processing
         */
        private void logWorkerCompletion(Instant startInstant) {
            System.out.println();
            System.out.printf(
                    "Worker %s finished after exploring %d plans, %d graph collisions and %d set collisions after %d ms.%n",
                    this.workerID,
                    this.plansExplored,
                    this.graphCollisions,
                    this.setCollisions,
                    Duration.between(startInstant, Instant.now()).toMillis()
            );
        }

        /**
         * Method to log exception (for clean code only)
         *
         * @param e Exception to log
         */
        private void logException(Exception e) {
            // Use a proper logging framework in production code
            System.err.printf("Worker %s: %s - %s%n", workerID, "Error processing action", e.getMessage());
        }
    }
}