package optimizer.algorithm.newalgs;

import core.structs.Tuple;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.aggregators.ResultAggregator;
import optimizer.algorithm.taskiterators.TaskIterator;

import java.math.BigInteger;
import java.util.concurrent.*;


/**
 * This class provides an easy way to implement every algorithm that can be
 * reduced to the producer-consumer paradigm.
 * <br>
 * Should we implement a TaskIterator, a Task, and a Result aggregator,
 * this class will handle everything else, and execute the algorithm
 * in a multithreaded way.
 * <br>
 * Algorithms that cannot be reduces to the producer-consumer
 * paradigm will either be implemented in a completely separate class,
 * or extend and override the methods of this class.
 */
public abstract class AbstractPlanBasedAlgorithm implements PlanBasedAlgorithm {

    protected Graph rootFlow;
    protected int numPlatforms;
    protected int numSites;
    protected int numOperators;
    protected BigInteger possiblePlans;
    protected BigInteger plansToExamine;
    protected int timeout;
    protected int numThreads;

    protected TaskIterator taskIterator;
    protected ResultAggregator resultAggregator;
    protected AlgorithmTerminationPredicate terminationPredicate;
    protected ThreadPoolExecutor algorithmExecutor;
    protected ScheduledExecutorService timeoutExecutor;
    protected ScheduledExecutorService statisticsExecutor;
    protected CompletionService<Tuple<Graph, Integer>> completionService;
    protected boolean setupCalled = false;
    protected boolean disableStats;

    private static volatile boolean timeoutReached = false;

    /**
     * Constructor
     * @param rootFlow The root flow of the application
     * @param numPlatforms The number of platforms
     * @param numSites The number of sites
     * @param timeout The timeout
     * @param numThreads The number of threads
     */
    public AbstractPlanBasedAlgorithm(Graph rootFlow,
                                      int numPlatforms,
                                      int numSites,
                                      int timeout,
                                      boolean disableStats,
                                      int numThreads,
                                      int queueSize) {
        this.rootFlow = rootFlow;
        this.numPlatforms = numPlatforms;
        this.numSites = numSites;
        this.timeout = timeout;
        this.numThreads = numThreads;
        this.numOperators = rootFlow.getVertices().size();
        this.possiblePlans = BigInteger.valueOf((long) this.numPlatforms * this.numSites).pow(this.numOperators);
        this.plansToExamine = this.possiblePlans;
        this.disableStats = disableStats;

        this.algorithmExecutor = new ThreadPoolExecutor(numThreads,
                numThreads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queueSize));

        this.completionService = new ExecutorCompletionService<>(algorithmExecutor);
        this.statisticsExecutor = Executors.newSingleThreadScheduledExecutor();
        this.timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Method to initialize the class's necessary components
     * @param taskIterator The TaskIterator object
     * @param resultAggregator The ResultAggregator object
     * @param terminationPredicate The AlgorithmTerminationPredicate object
     */
    @Override
    public void setup(TaskIterator taskIterator,
                      ResultAggregator resultAggregator,
                      AlgorithmTerminationPredicate terminationPredicate) {
        this.taskIterator = taskIterator;
        this.resultAggregator = resultAggregator;
        this.terminationPredicate = terminationPredicate;
        this.setupCalled = true;
    }

    /**
     * Method to print the necessary info before the algorithm's execution
     */
    protected void printBeforeExecution() {
        System.out.println("---------- Before execution info ----------");
        System.out.println("Root plan: " + rootFlow.getSignatureDashed());
        System.out.println("Number of operators: " + numOperators);
        System.out.println("Number of platforms: " + numPlatforms);
        System.out.println("Number of sites: " + numSites);
        System.out.println("Parallelism: " + numThreads);
        System.out.println("Possible plans: " + possiblePlans);
    }

    /**
     * Method to print necessary info after the algorithm's execution
     * @param result The OptimizationResult object to be printed
     */
    protected void printAfterExecution(OptimizationResult result) {
        System.out.println("\n");
        System.out.println("---------- After execution info ----------");
        result.print();
    }

    /**
     * Method to schedule the thread that
     * timeouts the algorithm after timeout ms
     */
    protected void scheduleTimeout() {
        timeoutExecutor.schedule(() -> {
            timeoutReached = true;
        }, timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * Method to schedule the thread that outputs the statistics
     * each 1s
     * @param startTime The time in ms when the method was called
     */
    protected void scheduleStatistics(long startTime) {
        statisticsExecutor.scheduleAtFixedRate(() -> {
            System.out.print("\rCompleted tasks: " + algorithmExecutor.getCompletedTaskCount() +
                    " | " + "Task count: " + algorithmExecutor.getTaskCount() +
                    " | " + "Running time: " + (System.currentTimeMillis() - startTime) + " ms");
            System.out.flush();  // Ensure output is flushed to the console
        }, 1000L, 1000L, TimeUnit.MILLISECONDS);
    }

    /**
     * Method to go through the algorithm execution
     * <br>
     * The method will call the setup method, print the information
     * before the execution, start the task generator,
     * process the results and print the information after the execution.
     * <br>
     * The method will also call the cleanup method.
     *
     */
    public Graph execute() {
        printBeforeExecution();
        long statisticsStartTime = System.currentTimeMillis();

        if (!disableStats) {
            scheduleStatistics(statisticsStartTime);
        }

        if (timeout != -1) {
            scheduleTimeout();
        }

        generateTasks();
        OptimizationResult optimizationResult = aggregateResults();
        printAfterExecution(optimizationResult);
        cleanUp();
        return optimizationResult.getBestPlan();
    }


    /**
     * Method to start the thread that generates the tasks.
     * <br>
     * The thread will keep trying to submit a task even when the queue is full.
     * No tasks are discarded.
     * <br>
     * The thread will stop when all tasks are submitted or when the timeout is reached
     */
    @Override
    public void generateTasks() {
        Thread taskGenerationThread = new Thread(() -> {
            while (taskIterator.hasNext() && !timeoutReached) {
                Callable<Tuple<Graph, Integer>> task = taskIterator.next();
                boolean isTaskSubmitted = false;
                while (!isTaskSubmitted) {
                    try {
                        completionService.submit(task);
                        isTaskSubmitted = true;
                    } catch (RejectedExecutionException ignored) {
                        // Do nothing
                    }
                }
            }
        });
        taskGenerationThread.start();
    }

    /**
     * Method to process the results of the tasks
     * <br>
     * The method will keep taking the results from the completion service
     * and will determine what the best plan is.
     * <br>
     * The method will also measure the duration of the execution.
     */
    @Override
    public OptimizationResult aggregateResults() {
        BigInteger completedTasks = BigInteger.ZERO;
        long startTime = System.currentTimeMillis();
        while (terminationPredicate.test(completedTasks) && !timeoutReached) {
            try {
                Future<Tuple<Graph, Integer>> future = completionService.take();
                Tuple<Graph, Integer> result = future.get();
                resultAggregator.addResult(result);
                completedTasks = completedTasks.add(BigInteger.valueOf(result._2));
            } catch (InterruptedException | ExecutionException e) {
                System.err.println(e.getMessage());
                e.getCause().printStackTrace(System.err);
            }
        }
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        return resultAggregator.getResults().withDuration(duration);
    }

    /**
     * Method to clean up the resources
     * <br>
     * The method will shut down the executor and will wait for the tasks to finish.
     * <br>
     * It will enforce a shutdown after 10 seconds if the tasks are not finished.
     */
    protected void cleanUp() {
        try {
            if (timeout != -1) { // Shutdown the timeoutExecutor only when a timeout was set
                timeoutExecutor.shutdownNow();
            }
            statisticsExecutor.shutdownNow();
            algorithmExecutor.shutdown();
            if (!algorithmExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                algorithmExecutor.shutdownNow();
                if (!algorithmExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    System.err.println("Executor did not terminate");
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        timeoutReached = false;
    }
}
