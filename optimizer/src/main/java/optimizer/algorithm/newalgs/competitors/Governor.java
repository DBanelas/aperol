package optimizer.algorithm.newalgs.competitors;

import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.graph.Vertex;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import java.util.*;

public class Governor {

    private static final String CLOUD_IDENTIFIER = "cloud";
    private final Graph rootFlow;
    private final Map<String, Double> pairLinks;
    private final Map<Integer, String> idToOperatorMapping;
    private final Map<String, ArrayList<String>> sitePlatformMapping;
    private final HashMap<String, Integer> siteMappingReverse;
    private final HashMap<String, Integer> platformMappingReverse;

    public Governor(Graph rootFlow,
                    Map<String, Double> pairLinks,
                    Map<Integer, String> idToOperatorMapping,
                    Map<String, ArrayList<String>> sitePlatformMapping,
                    HashMap<String, Integer> siteMappingReverse,
                    HashMap<String, Integer> platformMappingReverse) {
        this.rootFlow = rootFlow;
        this.pairLinks = pairLinks;
        this.idToOperatorMapping = idToOperatorMapping;
        this.sitePlatformMapping = sitePlatformMapping;
        this.siteMappingReverse = siteMappingReverse;
        this.platformMappingReverse = platformMappingReverse;
    }

    private Graph placeOperatorsUniformly(List<String> bestPathVertexList) {
        Graph bestPlan = new Graph(rootFlow);

       // Initialize the vertex list with the operators from the root flow
        List<Vertex> topo = bestPlan.getTopologicalOrder();
        int operatorCnt = topo.size();
        int deviceCnt   = bestPathVertexList.size();

        if (deviceCnt == 0) {
            throw new IllegalArgumentException("bestPathVertexList must contain at least one device");
        }

        // Calculate how many operators each device should get (uniform distribution)
        int baseLoad = operatorCnt / deviceCnt;
        int extra    = operatorCnt % deviceCnt;

        // For reproducibility
        Random rnd = new Random(7);


        int deviceIdx          = 0;
        int placedOnDevice     = 0;
        int quotaForThisDevice = baseLoad +
                (extra > 0 ? 1 : 0);
        if (extra > 0) extra--;

        // Iterate over the vertices in the topological order
        for (Vertex v : topo) {

            // Choose the site
            String siteToPlace = bestPathVertexList.get(deviceIdx);
            if (siteToPlace.contains(CLOUD_IDENTIFIER)) {
                siteToPlace = siteToPlace.replace("." + CLOUD_IDENTIFIER, "");
            }

            // Choose the platform
            ArrayList<String> availablePlatforms = sitePlatformMapping.get(siteToPlace);
            if (availablePlatforms == null || availablePlatforms.isEmpty()) {
                throw new IllegalStateException("No platforms found for site " + siteToPlace);
            }
            String platformToPlace = availablePlatforms.get(rnd.nextInt(availablePlatforms.size()));

            v.setSite(siteMappingReverse.get(siteToPlace));
            v.setPlatform(platformMappingReverse.get(platformToPlace));

            placedOnDevice++;
            if (placedOnDevice == quotaForThisDevice && deviceIdx < deviceCnt - 1) {
                // move to the next device
                deviceIdx++;
                placedOnDevice = 0;
                quotaForThisDevice = baseLoad + (extra > 0 ? 1 : 0);
                if (extra > 0) extra--;
            }
        }

        return bestPlan;
    }

    public Graph execute() {

        long startTime = System.currentTimeMillis();

        // Create the topology graph
        org.jgrapht.Graph<String, DefaultWeightedEdge> g = CompetitorUtil.createTopologyGraph(pairLinks);

        // Find the root node in the topology graph. Use the cloud identifier to find the root.
        String root = g.vertexSet().stream()
                .filter(v -> v.contains(CLOUD_IDENTIFIER))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No cloud node found in the topology graph"));

        // Find all leaves of the topology graph. All nodes except the root that have only one edge.
        List<String> leaves = new ArrayList<>();
        for (String v : g.vertexSet()) {
            if (!v.equals(root) && g.degreeOf(v) == 1) {
                leaves.add(v);
            }
        }

        // Compute all path lengths from each leaf to the root and keep the path with the lowest latency.
        DijkstraShortestPath<String, DefaultWeightedEdge> dijkstra =
                new DijkstraShortestPath<>(g);

        double bestPathLatency = Double.MAX_VALUE;
        List<String> bestPathVertexList = null;
        for (String leaf : leaves) {
            double leafPathLatencySum = dijkstra.getPath(leaf, root).getWeight();
            List<String> path = dijkstra.getPath(leaf, root).getVertexList();
            if (leafPathLatencySum < bestPathLatency) {
                bestPathLatency = leafPathLatencySum;
                bestPathVertexList = path;
            }
        }

        if (bestPathVertexList == null || bestPathVertexList.isEmpty()) {
            throw new IllegalStateException("No valid path found from any leaf to the root");
        }

        // 4) Uniformly place the operators in the path
        Graph bestPlan = placeOperatorsUniformly(bestPathVertexList);

        long endTime = System.currentTimeMillis();
//        System.out.println("Best path latency: " + bestPathLatency);
//        System.out.println("Best path vertices: " + bestPathVertexList);
//        System.out.println("Execution time: " + (endTime - startTime) + " ms");
        return bestPlan;
    }
}
