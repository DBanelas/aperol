package optimizer.algorithm.newalgs;

import optimizer.algorithm.graph.Graph;

public class OptimizationResult {
    private Graph bestPlan;
    private long completedTasks;
    private long duration;

    public OptimizationResult() {
        this.bestPlan = null;
        this.completedTasks = 0L;
        this.duration = 0L;
    }

    public OptimizationResult withBestPlan(Graph bestPlan) {
        this.bestPlan = bestPlan;
        return this;
    }

    public OptimizationResult withCompletedTasks(long completedTasks) {
        this.completedTasks = completedTasks;
        return this;
    }

    public OptimizationResult withDuration(long duration) {
        this.duration = duration;
        return this;
    }

    public void print() {
        if (bestPlan == null) {
            System.out.println("Best plan has not been set!");
            return;
        }

        System.out.println("Algorithm duration: " + this.duration);
        System.out.println("Completed tasks: " + this.completedTasks);
        System.out.println("Best plan signature: " + bestPlan.getSignatureDashed());
        System.out.println("Best plan score: " + bestPlan.getCost());
        System.out.println("Best plan structure: ");
        System.out.println();
        System.out.println(bestPlan);
    }

    public Graph getBestPlan() {
        return bestPlan;
    }
}
