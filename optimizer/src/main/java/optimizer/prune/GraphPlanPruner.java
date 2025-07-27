package optimizer.prune;

import optimizer.algorithm.graph.Graph;

import java.util.Collection;
import java.util.Set;

public interface GraphPlanPruner {
    boolean prune(Graph plan);
    int getSkylineSize();
    Set<String> addToSkyline(Graph graph);
}
