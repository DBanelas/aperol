package optimizer.algorithm.taskiterators;

import core.structs.Tuple;
import optimizer.algorithm.cost.CostEstimatorIface;
import optimizer.algorithm.graph.Graph;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

public abstract class AbstractTaskIterator implements TaskIterator {

    protected final Graph rootFlow;
    protected final Set<Integer> cloudOnlyOperatorIds;
    protected final Map<String, Integer> siteMappingReverse;
    protected final CostEstimatorIface costEstimation;
    protected final ArrayList<Tuple<Integer, Integer>> actions;

    public AbstractTaskIterator(Graph rootFlow,
                                Set<Integer> cloudOnlyOperatorIds,
                                Map<String, Integer> siteMappingReverse,
                                CostEstimatorIface costEstimation,
                                ArrayList<Tuple<Integer, Integer>> actions) {
        this.cloudOnlyOperatorIds = cloudOnlyOperatorIds;
        this.siteMappingReverse = siteMappingReverse;
        this.costEstimation = costEstimation;
        this.rootFlow = rootFlow;
        this.actions = actions;
    }
}
