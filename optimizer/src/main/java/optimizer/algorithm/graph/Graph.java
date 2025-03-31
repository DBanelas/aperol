package optimizer.algorithm.graph;

import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import optimizer.algorithm.cost.CostEstimatorIface;

import java.util.*;


/**
 * {@code Graph}: A graph is a list of vertices {@code List<Vertex>}.
 *
 * <p>Assumes that the {@code Vertex} name is unique.</p>
 */
public class Graph {

    private final List<Vertex> vertexList;
    private int graphCost;

    /**
     * Graph constructor.
     */
    public Graph() {
        this.vertexList = new ArrayList<>();
        this.graphCost = Integer.MAX_VALUE;
    }

    /**
     * Graph copy constructor with deep copy.
     *
     * @param other The graph to copy.
     */
    public Graph(Graph other) {
        this.vertexList = new ArrayList<>();
        this.graphCost = other.graphCost;

        Map<Vertex, Vertex> vertexMap = new HashMap<>();

        // First pass: create all vertices using clone and store the mapping
        for (Vertex oldVertex : other.vertexList) {
            Vertex newVertex = oldVertex.clone();
            this.vertexList.add(newVertex);
            vertexMap.put(oldVertex, newVertex);
        }

        // Second pass: update the adjacency list of each vertex
        for (Vertex oldVertex : other.vertexList) {
            Vertex newVertex = vertexMap.get(oldVertex);
            for (Vertex oldAdjVertex : oldVertex.getAdjVertices()) {
                newVertex.addAdj(vertexMap.get(oldAdjVertex));
            }
        }
    }

    public Graph(Graph other, int reqOperatorID, int reqSiteID) {
        this.vertexList = new ArrayList<>();
        this.graphCost = other.graphCost;

        Map<Vertex, Vertex> vertexMap = new HashMap<>();

        // First pass: create all vertices using clone and store the mapping
        for (Vertex oldVertex : other.vertexList) {
            Vertex newVertex = oldVertex.clone();
            if (newVertex.getOperatorId() == reqOperatorID) {
                newVertex.setSite(reqSiteID);
            }
            this.vertexList.add(newVertex);
            vertexMap.put(oldVertex, newVertex);
        }

        // Second pass: update the adjacency list of each vertex
        for (Vertex oldVertex : other.vertexList) {
            Vertex newVertex = vertexMap.get(oldVertex);
            for (Vertex oldAdjVertex : oldVertex.getAdjVertices()) {
                newVertex.addAdj(vertexMap.get(oldAdjVertex));
            }
        }
    }

    /**
     * Returns the cost of the graph.
     *
     * @return The cost value.
     */
    public int getGraphCost() {
        return this.graphCost;
    }

    /**
     * Add a new vertex.
     *
     * @param v The vertex to aad to the graph.
     */
    public void addVertex(Vertex v) {
        this.vertexList.add(v);
    }

    /**
     * Retrieves a vertex by name.
     *
     * @param key The name of the vertex.
     * @return The vertex if found or null.
     */
    public Vertex getVertex(int key) {
        for (Vertex u : this.vertexList) {
            if (u.getOperatorId() == key) {
                return u;
            }
        }
        return null;
    }

    /**
     * Returns the list of vertices.
     *
     * @return List of vertices.
     */
    public List<Vertex> getVertices() {
        return this.vertexList;
    }

    public DMatrix getGraphAsModelInput() throws XGBoostError {
        float[] inputVector = new float[this.getVertices().size()];
        for (int i = 0; i < this.getVertices().size(); i++) {
            inputVector[i] = (float) this.getVertices().get(i).getSite() - 1;
        }

        try {
            return new DMatrix(inputVector, 1, inputVector.length, Float.NaN);
        } catch (XGBoostError e) {
            // Handle the exception
            System.out.println("Cannot get the model input format from graph: " + this.getSignatureDashed()); // Log the exception (optional)
            System.exit(1); // Return null or handle it in another way
        }

        return null; // Just so it does not error. code is unreachable here.
    }

    /**
     * Updates a graph vertex and its dependencies.
     *
     * @param v The node to be updated.
     */
    public void updateVertex(Vertex v) {
        // identify the vertex if exists
        Vertex u = getVertex(v.getOperatorId());
        if (u == null) return;
        // update vertex dependencies (adjVertices in other vertices)
        int v_cnt = 0, pos = 0;
        for (Vertex x : this.vertexList) {
            if (x.getOperatorId() == (u.getOperatorId())) pos = v_cnt;
            v_cnt++;
            int cnt = 0;
            for (Vertex k : x.getAdjVertices()) {
                if (k.getOperatorId() == (u.getOperatorId())) {
                    x.getAdjVertices().set(cnt, v);
                }
                cnt++;
            }
        }
        // update vertex
        vertexList.set(pos, v);
    }

    /**
     * Gets source vertices in a graph.
     *
     * @return A set of source vertices.
     */
    public Collection<Vertex> getSources() {
        HashSet<Vertex> nds = new HashSet<>(this.vertexList);
        HashSet<Vertex> lst = new HashSet<>();
        for (Vertex u : this.vertexList) {
            lst.addAll(u.getAdjVertices());
        }
        nds.removeAll(lst);
        return nds;
    }

    /**
     * Computes graph cost as a sum of all vertex costs.
     *
     * @return The cost of the graph.
     */
    public int getCost() {
        return this.graphCost;
    }

    /**
     * Returns a signature of the graph.
     * <p>The signature follows the order of vertices in the {@code vertexList}
     * and has the form: PiSj|Pi+1Sj+1|...</p>
     *
     * @return The signature of the graph.
     */
    public String getSignature() {
        StringBuilder s = new StringBuilder();
        for (Vertex v : vertexList) {
            s.append("|").append(v.getPlatform()).append("-").append(v.getSite());
        }
        return s.toString().replaceFirst("\\|", "");
    }


    public String getSignatureDashed() {
        StringBuilder s = new StringBuilder();
        for (Vertex v : vertexList) {
            s.append("_").append(v.getPlatform()).append("-").append(v.getSite());
        }
        return s.toString().replaceFirst("_", "");
    }

    @Override
    public String toString() {
        StringBuilder f = new StringBuilder();
        for (Vertex v : vertexList) {
            f.append(v).append(" --> [").append(v.getAdjVertices()).append("]\n");               // show full vertex signature
        }
        return f.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(vertexList);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null)
            return false;
        if (getClass() != o.getClass())
            return false;
        Graph g = (Graph) o;
        // field comparison
        return Objects.equals(this.getVertices(), g.getVertices());
    }

    public void updateCost(CostEstimatorIface costEstimation) {
        this.graphCost = costEstimation.calculateCost(this);
    }
}
