package optimizer.algorithm.newalgs.competitors.springrelax;

import core.parser.network.AvailablePlatform;
import core.parser.network.Site;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.graph.Vertex;
import java.util.*;

public class SpringRelax {

    private static final int MAX_ITERS = 500;
    private static final double DAMPING = 0.10;
    private static final double K_SPRING = 1.0;
    private static final double REST_LEN = 1.0;

    private final Graph rootFlow;
    private final Map<String, Double> pairLats;
    private final Map<Integer, String> idToOperatorMapping;
    private final Map<Integer, Site> siteMapping;
    private final Map<String, ArrayList<String>> sitePlatformMapping;
    private final HashMap<String, Integer> siteMappingReverse;
    private final Map<Integer, AvailablePlatform> platformMapping;
    private final HashMap<String, Integer> platformMappingReverse;


    public SpringRelax(Graph rootFlow,
                       Map<String, Double> pairLats,
                       Map<Integer, String> idToOperatorMapping,
                       Map<String, ArrayList<String>> sitePlatformMapping,
                       Map<Integer, Site> siteMapping,
                       HashMap<String, Integer> siteMappingReverse,
                       Map<Integer, AvailablePlatform> platformMapping,
                       HashMap<String, Integer> platformMappingReverse) {
        this.rootFlow = rootFlow;
        this.pairLats = pairLats;
        this.idToOperatorMapping = idToOperatorMapping;
        this.sitePlatformMapping = sitePlatformMapping;
        this.siteMapping = siteMapping;
        this.siteMappingReverse = siteMappingReverse;
        this.platformMapping = platformMapping;
        this.platformMappingReverse = platformMappingReverse;
    }

    /**
     * Method to calculate the forces acting on each operator based on their coordinates.
     * @param operatorCoords The map that contains the coordinates of each operator
     * @return A map containing the net forces acting on each operator
     */
    private Map<String, double[]> calculateForces(Map<String, double[]> operatorCoords) {

        // initialize net-force container for every operator
        Map<String, double[]> forces = new HashMap<>();
        operatorCoords.forEach((op, p) -> forces.put(op, new double[] {0.0, 0.0}));

        final double EPS = 1e-6;
        Set<Long> seen = new HashSet<>();

        for (Vertex v1 : rootFlow.getVertices()) {
            String op1 = idToOperatorMapping.get(v1.getOperatorId());
            double[] p1 = operatorCoords.get(op1);

            for (Vertex v2 : v1.getAdjVertices()) {
                String op2 = idToOperatorMapping.get(v2.getOperatorId());

                if (op1.equals(op2)) continue;

                int id1 = v1.getOperatorId();
                int id2 = v2.getOperatorId();
                long key = id1 < id2
                        ? (((long) id1) << 32) | id2
                        : (((long) id2) << 32) | id1;

                if (!seen.add(key)) continue;

                double[] p2 = operatorCoords.get(op2);

                double dx = p1[0] - p2[0];
                double dy = p1[1] - p2[1];
                double dist = Math.hypot(dx, dy);
                if (dist < EPS) continue;

                double displacement = dist - REST_LEN;
                double factor      = -K_SPRING * displacement / dist;

                double fx = factor * dx;
                double fy = factor * dy;


                forces.get(op1)[0] += fx;
                forces.get(op1)[1] += fy;
                forces.get(op2)[0] -= fx;
                forces.get(op2)[1] -= fy;
            }
        }
        return forces;
    }

    public Graph execute() {

        NCSConverter ncsConverter = new NCSConverter(this.pairLats);
        ncsConverter.build();

        // Coordinates of every operator at the current moment
        Map<String, double[]> operatorCoords = new HashMap<>();

        for (Vertex v : rootFlow.getTopologicalOrder()) {
            String operatorName = idToOperatorMapping.get(v.getOperatorId());
            String operatorSite = siteMapping.get(v.getSite()).getSiteName();
            operatorCoords.put(operatorName, ncsConverter.coord(operatorSite));
        }

        for (int iter = 1; iter <= MAX_ITERS; iter++) {
            Map<String, double[]> forces = calculateForces(operatorCoords);

            // Apply forces
            for (var entry : forces.entrySet()) {
                String op = entry.getKey();
                double[] f = entry.getValue();
                operatorCoords.computeIfPresent(op, (k, v) -> {
                    v[0] += DAMPING * f[0];
                    v[1] += DAMPING * f[1];
                    return v;
                });
            }
        }

        // Build bestPlan from final coords
        Graph bestPlan = new Graph(rootFlow);
        for (Vertex v : bestPlan.getTopologicalOrder()) {
            String opName = idToOperatorMapping.get(v.getOperatorId());
            String newSite = findClosestSite(ncsConverter, operatorCoords.get(opName));
            String rootPlatform = platformMapping.get(v.getPlatform()).getPlatformName();
            Set<String> availablePlatforms = new HashSet<>(sitePlatformMapping.get(newSite));

            if (!availablePlatforms.contains(rootPlatform)) {
                // If the platform is not available at the new site, choose the best one (java)
                v.setPlatform(platformMappingReverse.get("java"));
            }

            if (newSite != null) {
                v.setSite(siteMappingReverse.get(newSite));
            }
        }
        return bestPlan;
    }


    private String findClosestSite(NCSConverter ncsConverter, double[] operatorCoords) {
        String closestSite = null;
        double minDistance = Double.MAX_VALUE;

        for (String site : siteMappingReverse.keySet()) {
            if (site.equals("UNKNOWN_SITE")) continue;

            double[] siteCoords = ncsConverter.coord(site);

            double distance = Math.hypot(operatorCoords[0] - siteCoords[0], operatorCoords[1] - siteCoords[1]);
            if (distance < minDistance) {
                minDistance = distance;
                closestSite = site;
            }
        }

        return closestSite;
    }
}
