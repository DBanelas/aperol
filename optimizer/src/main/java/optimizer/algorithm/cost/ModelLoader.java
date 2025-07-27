package optimizer.algorithm.cost;

import ml.dmlc.xgboost4j.java.XGBoost;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.XGBoostError;

public class ModelLoader {
    private final String modelDirectory;

    public ModelLoader(String modelDirectory) {
        this.modelDirectory = modelDirectory;
    }

    public Booster loadModel(String workflowName, int networkSize) throws XGBoostError {
        String modelPath = modelDirectory + "/" + ".model";
        return XGBoost.loadModel(modelPath);
    }
}
