package optimizer.prune;

import optimizer.algorithm.graph.Graph;

import java.util.Collection;

public interface GraphPlanPruner {
    boolean prune(Graph plan);

    void pruneList(Collection<Graph> plan);

    int getPrunedPlans();
}
