package optimizer.algorithm.cost;
import ml.dmlc.xgboost4j.java.XGBoostError;
import optimizer.algorithm.graph.Graph;

import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.XGBoost;

import java.nio.file.Paths;

public class XGBRegressorCostEstimator implements CostEstimatorIface {

    private Booster scoreModel;
    private String modelDirectory;
    private String modelWorkflow;
    private String modelNetwork;
    private Booster migrationCostModel;


    public void setModelDirectory(String modelDirectory) {
        this.modelDirectory = modelDirectory;
    }

    public void setModelWorkflow(String modelWorkflow) {
        this.modelWorkflow = modelWorkflow;
    }

    public void setModelNetwork(String modelNetwork) {
        this.modelNetwork = modelNetwork;
    }

    public void loadModels() throws XGBoostError {
        String modelsPrefix = Paths.get(this.modelDirectory, this.modelWorkflow).toString();
        String scoreModelPath = Paths.get(modelsPrefix, this.modelWorkflow + "_" + this.modelNetwork + "_score_model.bin").toString();
        String migrationCostModelPath = Paths.get(modelsPrefix, this.modelWorkflow + "_" + this.modelNetwork + "_migration_cost_model.bin").toString();
        this.scoreModel = XGBoost.loadModel(scoreModelPath);
        this.migrationCostModel = XGBoost.loadModel(migrationCostModelPath);
    }

    @Override
    public int calculateCost(Graph flow) {
        return getRealCost(flow) - getMigrationCost(flow);
    }

    @Override
    public int getMigrationCost(Graph flow) {
       try {
           return (int) (1 * this.migrationCostModel.predict(flow.getGraphAsModelInput())[0][0]);
       } catch (XGBoostError e) {
           System.out.println("Could not get the score for " + flow.getSignatureDashed());
           System.exit(1);
       }
       return 0;
    }

    @Override
    public int getRealCost(Graph flow) {
        try {
            return (int) this.scoreModel.predict(flow.getGraphAsModelInput())[0][0];
        } catch (XGBoostError e) {
            System.out.println("Could not get the score for " + flow.getSignatureDashed());
            System.exit(1);
        }
        return 0;
    }
}
