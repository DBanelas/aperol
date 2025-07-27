package optimizer.algorithm.newalgs.competitors;

import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import java.util.Map;

public class CompetitorUtil {

    public static org.jgrapht.Graph<String, DefaultWeightedEdge> createTopologyGraph(Map<String, Double> pairLinks) {
        org.jgrapht.Graph<String, DefaultWeightedEdge> topologyGraph =
                new SimpleWeightedGraph<>(DefaultWeightedEdge.class);

        // Add vertices and edges based on the pairLinks
        for (Map.Entry<String, Double> entry : pairLinks.entrySet()) {
            String[] nodes = entry.getKey().split(":");
            String source = nodes[0];
            String target = nodes[1];
            double latency = entry.getValue();

            topologyGraph.addVertex(source);
            topologyGraph.addVertex(target);
            DefaultWeightedEdge edge = topologyGraph.addEdge(source, target);
            topologyGraph.setEdgeWeight(edge, latency);
        }

        return topologyGraph;
    }
}
