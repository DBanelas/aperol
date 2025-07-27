package optimizer.algorithm.tasks;

import core.structs.Tuple;
import optimizer.algorithm.cost.PlanCostEstimatorInterface;
import optimizer.algorithm.cost.PlanDagStarCostEstimator;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.graph.Vertex;
import optimizer.algorithm.newalgs.AlgorithmUtils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.List;

public class BatchTask implements Callable<Tuple<Graph, Integer>> {

    private final Graph rootFlow;
    private final int numOperators;
    private final BigInteger batchSize;
    private final BigInteger startPlanNo;
    private final List<Tuple<Integer, Integer>> actions;
    private final int targetBase;
    private final PlanCostEstimatorInterface costEstimation;
    private final long timeoutTimestamp;
    private final boolean randomize;

    public BatchTask(Graph rootFlow,
                     PlanCostEstimatorInterface costEstimation,
                     BigInteger startPlanNo,
                     BigInteger batchSize,
                     List<Tuple<Integer, Integer>> actions,
                     int targetBase,
                     long timeoutTimestamp,
                     boolean randomize) {
        this.timeoutTimestamp = timeoutTimestamp;
        this.randomize = randomize;
        this.rootFlow = rootFlow;
        this.numOperators = rootFlow.getVertices().size();
        this.batchSize = batchSize;
        this.startPlanNo = startPlanNo;
        this.actions = actions;
        this.targetBase = targetBase;
        this.costEstimation = costEstimation;
    }

    /**
     * Method to process a given plan according to the actions that need to be applied to it
     * @param plan The plan to process.
     * @param actionsToApply The actions to apply to the plan.
     */
    private void processPlan(Graph plan, ArrayList<Integer> actionsToApply) {
        int actionNo = 0;
        for (Vertex v : plan.getVertices()) {
            Tuple<Integer, Integer> action = actions.get(actionsToApply.get(actionNo++));
            int platform = action._1;
            int site = action._2;
            v.setPlatform(platform);
            v.setSite(site);
        }
        plan.updateCost(costEstimation);
    }

    @Override
    public Tuple<Graph, Integer> call() {
        Graph bestPlan = new Graph(rootFlow);
        int examinedPlans = 0;
        Random random = new Random();

        for (BigInteger planNo = BigInteger.ZERO; planNo.compareTo(batchSize) < 0; planNo = planNo.add(BigInteger.ONE)) {
            if (System.currentTimeMillis() > timeoutTimestamp) {
                break; // Stop if the timeout has been reached
            }

            // Pick a random plan number between startPlanNo and startPlanNo + batchSize
            BigInteger randomPlanNo = startPlanNo.add(BigInteger.valueOf(random.nextInt(batchSize.intValue())));
            System.out.println("Thread: " + Thread.currentThread().getName() + " - Processing plan number: " + randomPlanNo);

            ArrayList<Integer> actionsToApply = AlgorithmUtils.convertToBaseWithPadding(randomPlanNo, targetBase, numOperators);
            Graph newPlan = new Graph(rootFlow);
            processPlan(newPlan, actionsToApply);

            // If costEstimation is an instance of PlanDagStarCostEstimator, minimize the cost, else maximize
            if (costEstimation instanceof PlanDagStarCostEstimator) {
                if (newPlan.getCost() < bestPlan.getCost()) {
                    bestPlan = newPlan;
                }
            } else {
                if (newPlan.getCost() > bestPlan.getCost()) {
                    bestPlan = newPlan;
                }
            }
            examinedPlans++;
        }

        return new Tuple<>(bestPlan, examinedPlans);
    }
}