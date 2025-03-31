package optimizer.algorithm.tasks;

import core.structs.Tuple;
import optimizer.algorithm.newalgs.AlgorithmUtils;
import optimizer.algorithm.cost.CostEstimatorIface;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.graph.Vertex;

import java.math.BigInteger;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Callable;

public class SinglePlanTask implements Callable<Tuple<Graph, Integer>> {

    private final Graph rootFlow;
    private final int numOperators;
    private final List<Tuple<Integer, Integer>> actions;
    private final BigInteger planNo;
    private final int targetBase;
    private final CostEstimatorIface costEstimation;

    public SinglePlanTask(Graph rootFlow,
                          CostEstimatorIface costEstimation,
                          List<Tuple<Integer, Integer>> actions,
                          BigInteger planNo,
                          int targetBase) {
        this.rootFlow = rootFlow;
        this.actions = actions;
        this.planNo = planNo;
        this.costEstimation = costEstimation;
        this.targetBase = targetBase;
        this.numOperators = this.rootFlow.getVertices().size();
    }

    @Override
    public Tuple<Graph, Integer> call() {
        ArrayList<Integer> actionsToApply = AlgorithmUtils.convertToBaseWithPadding(planNo, targetBase, numOperators);

        Graph flow = new Graph(rootFlow);
        int actionNo = 0;
        for (Vertex v : flow.getVertices()) {
            Tuple<Integer, Integer> action = actions.get(actionsToApply.get(actionNo++));
            int platform = action._1;
            int site = action._2;
            v.setSite(site);
            v.setPlatform(platform);
        }

        flow.updateCost(this.costEstimation);
//        System.out.println(flow.getSignatureDashed() + " : " + flow.getCost());
        return new Tuple<>(flow, 1); // one plan is examined per worker
    }
}
