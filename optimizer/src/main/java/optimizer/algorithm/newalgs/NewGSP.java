package optimizer.algorithm.newalgs;

import core.parser.network.AvailablePlatform;
import core.parser.network.Site;
import core.structs.Tuple;
import optimizer.algorithm.cost.DagStarCostEstimator;
import optimizer.algorithm.cost.PlanCostEstimator;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.graph.Vertex;
import optimizer.algorithm.cost.CostEstimatorIface;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;


/**
 * Algorithm NewGSP: Find the best action across the entire flow and repeat until all assignments are completed.
 * <p>(1) Start with the initial flow</p>
 * <p>(2) Find the action that produces the lowest flow cost</p>
 * <p>(3) Update the flow graph with the action and repeat</p>
 *
 * Important note by Dimitrios Banelas:
 * Each action must be performed independently of the previous actions.
 * That means that we must revert each action after applying it and calculating the score.
 * <br>
 * Not reverting the action will lead to a wrong algorithm, as for every vertex
 * we will consider the previous actions as well.
 */
public class NewGSP {
    static List<Tuple<Integer, Integer>> actions = new ArrayList<>();
    static int bestCost = Integer.MAX_VALUE;
    static int visited = 0;
    private static CostEstimatorIface costEstimation;

    /**
     * Method to schedule the thread that outputs the statistics
     * each 1s
     * @param startTime The time in ms when the method was called
     */
    private static void scheduleStatistics(ScheduledExecutorService statisticsExecutor,
                                           long startTime) {
        statisticsExecutor.scheduleAtFixedRate(() -> {
            System.out.print("\rCompleted tasks: " + visited +
                    " | " + "Running time: " + (System.currentTimeMillis() - startTime) + " ms" +
                    " | " + "Best score: " + bestCost +
                    " | " + "Failed sims: " + PlanCostEstimator.failedSimulations);
            System.out.flush();  // Ensure output is flushed to the console
        }, 1000L, 1000L, TimeUnit.MILLISECONDS);
    }


    /**
     * Driver for the algorithm, including initialization and generation of the proposed plan.
     *
     * @param flow            - the initial flow
     * @param platformMapping - number of changePlatform actions
     * @param siteMapping     - number of changeSite actions
     * @param costEstimation  - the cost estimation interface
     * @param timeout         - the timeout for the algorithm
     * @return                - the best plan
     */
    public static Graph createPlanSpaceGSProgressive(Graph flow,
                                                     Map<Integer, AvailablePlatform> platformMapping,
                                                     Map<Integer, Site> siteMapping,
                                                     Set<Integer> cloudOnlyOperatorIds,
                                                     CostEstimatorIface costEstimation,
                                                     ExecutorService executorService,
                                                     int maxPlans,
                                                     int timeout) {

        NewGSP.costEstimation = costEstimation;
        ScheduledExecutorService statisticsExecutor  = Executors.newSingleThreadScheduledExecutor();
        //Get the sizes
        int platforms = platformMapping.size() - 1;
        int sites = siteMapping.size() - 1;

        // get all actions (skip P0, S0)
        for (int i = 0; i < platforms; i++) {
            for (int j = 0; j < sites; j++) {
                actions.add(new Tuple<>((i + 1), (j + 1)));
            }
        }

        int operators = flow.getVertices().size();
        int possiblePlans = (int) Math.pow((platforms * sites), operators);

        scheduleStatistics(statisticsExecutor, System.currentTimeMillis());

        System.out.println("Visited:  " + visited);
        printInfoBeforeExecution(operators, platforms, sites, possiblePlans, actions);

        // start-time
        Instant t1 = Instant.now();

        //Run the task
        AtomicReference<Graph> bestPlanRef = new AtomicReference<>();
        Future<?> task = executorService.submit(() -> checkAction(new Graph(flow), cloudOnlyOperatorIds, maxPlans, bestPlanRef));

        //Wait for task to complete or a timeout has reached
        try {
            task.get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            System.out.println("Executor service awaitTermination was interrupted.");
        } catch (TimeoutException e) {
            System.out.println("Algorithm timed out.");
        } catch (ExecutionException e) {
            System.out.printf("Executor encountered the following error: [%s]%n", e);
        }

        if (bestPlanRef.get() == null) {
            throw new IllegalStateException("Failed to produce any valid plans.");
        }
        Graph bestPlan = bestPlanRef.get();

        // end-time
        long t = Duration.between(t1, Instant.now()).toMillis();
        printInfoAfterExecution(t, bestPlan, visited);
        statisticsExecutor.shutdownNow();

//        System.out.println("-------------------------- PLAN METRICS -----------------------");
//        System.out.println();
//        System.out.println("--------- ROOT PLAN ---------");
//        ((PlanCostEstimator) costEstimation).printMetricsForFlow(flow);
//        System.out.println();
//        System.out.println("--------- BEST PLAN ---------");
//        ((PlanCostEstimator) costEstimation).printMetricsForFlow(bestPlan);

        return bestPlan;
    }

    private static void printInfoBeforeExecution(int operators,
                                                 int platforms,
                                                 int sites,
                                                 long possiblePlans,
                                                 List<Tuple<Integer, Integer>> actions) {
        System.out.println("\n--------\n[" + Thread.currentThread().getStackTrace()[1].getMethodName() + "]");
        System.out.println("#operators: " + operators + " , #platforms: " + platforms + " , #sites: " + sites);
        System.out.println("possible #plans: (" + platforms + " * " + sites + ") ^ " + operators + " = " + possiblePlans);
//        System.out.println("possible actions: " + actions + "\n");
    }

    public static void cleanUp() {
        bestCost = Integer.MAX_VALUE;
        actions = new ArrayList<>();
        visited = 0;
    }


    /**
     * Prints the information after the execution of the algorithm.
     * @param t The time taken for the execution.
     * @param finalPlan The final plan.
     * @param completedTasks The number of completed tasks.
     */
    private static void printInfoAfterExecution(long t,
                                                Graph finalPlan,
                                                int completedTasks) {
        System.out.println();
        System.out.println("Elapsed time: " + t + " ms");
        System.out.println("Number of completed tasks: " + completedTasks);
        System.out.println("Min plan cost: " + finalPlan.getCost());
        System.out.println("Min plan signature: " + finalPlan.getSignatureDashed());
        System.out.println("Min plan graph: \n" + finalPlan);
    }

    /**
     * Identifies the cheapest action for a flow, updates the flow, and repeat.
     * Side effect: updates the bestPlan reference.
     * @param flow - an input graph
     *
     */
    private static void checkAction(Graph flow,
                                    Set<Integer> cloudOnlyOperatorIds,
                                    int maxPlans,
                                    AtomicReference<Graph> bestPlan) {
        Graph g = new Graph(flow); // G'
        Graph gState;    // Gstate

        Vertex bestVertex = null;
        Tuple<Integer, Integer> bestAction = null;
        List<Vertex> vertexList = g.getVertices();

        // until we test all vertices
        while (!vertexList.isEmpty()) {
            bestCost = Integer.MIN_VALUE;   // minCost per episode
            gState = new Graph(g); // Gstate = G'

            // for each vertex
            for (Vertex v : vertexList) {
                // Changes are applied to G'
                // Get the vertexId of the operator.
                int vertexID = v.getOperatorId();
                Vertex realVertex = g.getVertex(vertexID);

                if (cloudOnlyOperatorIds.contains(vertexID)) continue; // Do not try to move a cloudOnly operator

                // try all actions
                for (Tuple<Integer, Integer> a : actions) {
                    visited++; // counts the plans visited

                    // get action
                    int s = a._2;
                    int p = a._1;
                    int ogPlatform = realVertex.getPlatform();
                    int ogSite = realVertex.getSite();

                    // update vertex
                    if (realVertex.getPlatform() != p) {
                        realVertex.setPlatform(p);
                    }

                    if (realVertex.getSite() != s) {
                        realVertex.setSite(s);
                    }

                    g.updateCost(costEstimation);
                    int cst = g.getCost();

                    // keeps the cheapest action a per vertex v
                    if (costEstimation instanceof DagStarCostEstimator) {
                        if (cst < bestCost || bestAction == null) {
                            bestCost = cst;
                            bestVertex = v;
                            bestAction = new Tuple<>(p, s);
                        }
                    } else {
                        if (cst > bestCost || bestAction == null) {
                            bestCost = cst;
                            bestVertex = v;
                            bestAction = new Tuple<>(p, s);
                        }
                    }

                    // Restore vertex to original state
                    realVertex.setPlatform(ogPlatform);
                    realVertex.setSite(ogSite);
                }
            }

            if (bestAction == null) {
                throw new IllegalStateException("No valid platform-site combination was found.");
            }

            // update intermediate state
            // G' = Gstate
            // Best action is applied to G'
            g = new Graph(gState);
            Vertex u = g.getVertex(bestVertex.getOperatorId());
            u.setPlatform(bestAction._1);
            u.setSite(bestAction._2);
            g.updateCost(costEstimation);
            bestPlan.set(g);

            // remove visited vertex and clean temp params
            vertexList.remove(bestVertex);
            bestVertex = null;
            bestAction = null;
        }
    }


}
