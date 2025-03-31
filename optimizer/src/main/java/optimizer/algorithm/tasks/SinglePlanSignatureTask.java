package optimizer.algorithm.tasks;

import core.structs.Tuple;
import optimizer.algorithm.cost.CostEstimatorIface;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.graph.Vertex;

import java.util.concurrent.Callable;

public class SinglePlanSignatureTask implements Callable<Tuple<Graph, Integer>> {
    private final Graph rootFlow;
    private final CostEstimatorIface costEstimation;
    private final String signature;

    public SinglePlanSignatureTask(Graph rootFlow,
                                   String signature,
                                   CostEstimatorIface costEstimation) {
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
            int site = Integer.parseInt(signatureParts[i++].split("-")[1]);
            v.setPlatform(1);
            v.setSite(site);
        }
        flow.updateCost(costEstimation);
        return new Tuple<>(flow, 1);
    }
}
