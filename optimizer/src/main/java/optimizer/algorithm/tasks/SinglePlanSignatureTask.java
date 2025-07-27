package optimizer.algorithm.tasks;

import core.structs.Tuple;
import optimizer.algorithm.cost.PlanCostEstimatorInterface;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.graph.Vertex;

import java.util.concurrent.Callable;

public class SinglePlanSignatureTask implements Callable<Tuple<Graph, Integer>> {
    private final Graph rootFlow;
    private final PlanCostEstimatorInterface costEstimation;
    private final String signature;

    public SinglePlanSignatureTask(Graph rootFlow,
                                   String signature,
                                   PlanCostEstimatorInterface costEstimation) {
        this.rootFlow = rootFlow;
        this.costEstimation = costEstimation;
        this.signature = signature;
    }

    @Override
    public Tuple<Graph, Integer> call() throws Exception {
        Graph flow = new Graph(rootFlow);
        String[] signatureParts = signature.split("_");
        int i = 0;
        for (Vertex v : flow.getVertices()) {
            int platform = Integer.parseInt(signatureParts[i].split("-")[0]);
            int site = Integer.parseInt(signatureParts[i].split("-")[1]);
            v.setPlatform(platform);
            v.setSite(site);
            i++;
        }
        flow.updateCost(costEstimation);
        return new Tuple<>(flow, 1);
    }
}
