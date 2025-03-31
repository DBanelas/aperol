package optimizer.algorithm.tasks;

import core.structs.Tuple;
import optimizer.algorithm.cost.CostEstimatorIface;
import optimizer.algorithm.cost.DagStarCostEstimator;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.graph.Vertex;
import optimizer.algorithm.newalgs.AlgorithmUtils;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.List;

public class BatchTask implements Callable<Tuple<Graph, Integer>> {

    private final Graph rootFlow;
    private final int numOperators;
    private final long batchSize;
    private final long startPlanNo;
    private final List<Tuple<Integer, Integer>> actions;
    private final int targetBase;
    private final CostEstimatorIface costEstimation;

    public BatchTask(Graph rootFlow,
                     CostEstimatorIface costEstimation,
                     long startPlanNo,
                     long batchSize,
                     List<Tuple<Integer, Integer>> actions,
                     int targetBase) {
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
        // Sample sequential plans
        for (long planNo = startPlanNo; planNo < startPlanNo + batchSize; planNo++) {
            ArrayList<Integer> actionsToApply = AlgorithmUtils.convertToBaseWithPadding(planNo, targetBase, numOperators);
            Graph newPlan = new Graph(rootFlow);
            processPlan(newPlan, actionsToApply);
            // If costEstimation is instance of DagStarCostEstimator, we need to minimize the cost, else maximize
            if (costEstimation instanceof DagStarCostEstimator) {
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
