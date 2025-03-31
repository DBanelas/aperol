package optimizer.algorithm.cost.distribution;

import optimizer.algorithm.graph.Graph;
import org.apache.commons.math3.distribution.GammaDistribution;
import org.apache.commons.math3.distribution.LogNormalDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;

import java.util.List;

/**
 * Inner class to represent a distribution type and its parameters.
 */
public class Distribution implements SamplingMethod {
    private final String type;
    private final List<Double> parameters;

    public Distribution(String type, List<Double> parameters) {
        this.type = type;
        this.parameters = parameters;
    }

    public double getSample(Graph flow, int networkSize, boolean isMetricScore) {
        int metricSeed = isMetricScore ? 1 : 2;
        JDKRandomGenerator randomGenerator = new JDKRandomGenerator();
        randomGenerator.setSeed(flow.getSignatureDashed().hashCode() + networkSize + metricSeed);
        double s, a, loc, scale;

        switch (type) {
            case "normal":
                loc = parameters.get(0);
                scale = parameters.get(1);
                NormalDistribution normalDist = new NormalDistribution(randomGenerator, loc, scale);
                return normalDist.sample();
            case "lognorm":
                s = parameters.get(0);
                loc = parameters.get(1);
                scale = parameters.get(2);
                double mu = Math.log(scale);
                LogNormalDistribution logNormalDist = new LogNormalDistribution(randomGenerator, mu, s);
                return logNormalDist.sample() + loc;
            case "gamma":
                a = parameters.get(0);
                loc = parameters.get(1);
                scale = parameters.get(2);
                GammaDistribution gammaDist = new GammaDistribution(randomGenerator, a, scale);
                return gammaDist.sample() + loc;
            default:
                throw new UnsupportedOperationException("Unsupported distribution type: " + type);
        }
    }
}