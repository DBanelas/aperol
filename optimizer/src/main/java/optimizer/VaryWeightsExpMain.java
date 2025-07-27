package optimizer;

import core.parser.dictionary.Dictionary;
import core.parser.network.Network;
import core.parser.workflow.OptimizationRequest;
import core.structs.BoundedPriorityQueue;
import core.utils.JSONSingleton;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import optimizer.plan.OptimizationPlan;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class VaryWeightsExpMain {

    static int threads = 4;
    static double percentage = 3000;
    static int timeout = 60000;
    static String[] ALGORITHMS_1 = {"e-gsp", "e-qp"};
    static String[] ALGORITHMS_2 = {"e-hsp", "e-escp", "e-esq"};
    //    / (wT, wL, wN, wM) = (throughput, latency, network-usage, migration)
    static double[][] WEIGHT_VECTORS = {
            /* A Baseline */
            {0.25, 0.25, 0.25, 0.25},

            /* B  Single-emphasis */
            {0.70, 0.10, 0.10, 0.10},   // throughput-heavy
            {0.10, 0.70, 0.10, 0.10},   // latency-heavy
            {0.10, 0.10, 0.70, 0.10},   // network-heavy
            {0.10, 0.10, 0.10, 0.70},   // migration-heavy

            /* C  Pairwise trade-offs */
            {0.4, 0.4, 0.1, 0.1},
            {0.4, 0.1, 0.4, 0.1},
            {0.4, 0.1, 0.1, 0.4},
            {0.1, 0.4, 0.4, 0.1},
            {0.1, 0.4, 0.1, 0.4},
            {0.1, 0.1, 0.4, 0.4},

            /* E  Random interior */
            {0.03, 0.43, 0.31, 0.23},
            {0.07, 0.21, 0.59, 0.13},
            {0.15, 0.34, 0.10, 0.41},
            {0.22, 0.06, 0.51, 0.21},
            {0.44, 0.11, 0.29, 0.16},
    };

    public static void main(String[] args) {
        ArgumentParser argumentParser = createArgumentParser();
        Namespace ns = argumentParser.parseArgsOrFail(args);

        String datasetFile = ns.getString("datasetFile");

        String workflowName = "yahoo-benchmark";
        int networkName = 127;
        String workflowDirectory = ns.getString("workflowDirectory");
        String networkDirectory = ns.getString("networkDirectory");

        String iFogSimWorkflowPath = Path.of(workflowDirectory, workflowName + "-ifogsim.json").toString();
        String optimizerWorkflowPath = Path.of(workflowDirectory, workflowName + "_optimizer.json").toString();

        String networkPath = Path.of(networkDirectory, "net_" + networkName, "network_" + networkName + "_1_fitiot.json").toString();
        String pairLatsPath = Path.of(networkDirectory, "net_" + networkName, "network_" + networkName + "_1_pair_lat.txt").toString();
        String pairLinksPath = Path.of(networkDirectory, "net_" + networkName, "network_" + networkName + "_1_links.txt").toString();
        String dictionaryPath = "/yahoo/dict_" + networkName + "_1_" + workflowName + "-ifogsim" + ".json";
        String dictionaryContent = getDictionaryContent(dictionaryPath);

        //Get the resources bundle
        try {

            OptimizationResourcesBundle bundle = getBundle(dictionaryContent, networkPath,
                    optimizerWorkflowPath, threads, timeout, pairLatsPath,
                    pairLinksPath, datasetFile,
                    iFogSimWorkflowPath,
                    workflowName, String.valueOf(networkName));

            String resultsPath = conductExperiment(bundle);
            System.out.println("Experiment completed successfully. Results saved to: " + resultsPath);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    private static String conductExperiment(OptimizationResourcesBundle bundle) {
        String resultsPath = "vary_weights_results.csv";
        HashMap<String, Map<String, VaryWeightsResult>> results = new HashMap<>(); // stores results of all algorithms per weight vector
        for (double[] weightVec : WEIGHT_VECTORS) {
            String weightID = Arrays.toString(weightVec);
            results.putIfAbsent(weightID, new HashMap<>());

            double durationSum = 0.0;
            for (String algorithm : ALGORITHMS_1) {
                try {
                    LightweightFlowOptimizer algorithmExecutor = new LightweightFlowOptimizer(algorithm, weightVec, percentage, 1);

                    ExecutorService executorService = Executors.newFixedThreadPool(threads);
                    setupAlgorithmExecutor(algorithmExecutor, bundle, executorService);

                    algorithmExecutor.doWork();
                    VaryWeightsResult result = algorithmExecutor.getResult();

                    durationSum += result.getDuration();
                    results.get(weightID).put(algorithm, result);
                    result.writeToCsv(Path.of(resultsPath), true);

                    algorithmExecutor.teardown();
                    shutdownExecutor(executorService);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Error while setting up the algorithm " + algorithm + " with weights " + Arrays.toString(weightVec) + ": " + e.getMessage());
                }
            }

            int averageDuration = (int) ((durationSum + 200) / ALGORITHMS_1.length);

            for (String algorithm : ALGORITHMS_2) {
                if (algorithm.equals("e-hsp")) bundle.setTimeout(60_000);
                else bundle.setTimeout(averageDuration);
                try {
                    LightweightFlowOptimizer algorithmExecutor = new LightweightFlowOptimizer(algorithm, weightVec, percentage, 1);

                    ExecutorService executorService = Executors.newFixedThreadPool(threads);
                    setupAlgorithmExecutor(algorithmExecutor, bundle, executorService);

                    algorithmExecutor.doWork();
                    VaryWeightsResult result = algorithmExecutor.getResult();

                    durationSum += result.getDuration();
                    results.get(weightID).put(algorithm, result);
                    result.writeToCsv(Path.of(resultsPath), true);

                    algorithmExecutor.teardown();
                    shutdownExecutor(executorService);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Error while setting up the algorithm " + algorithm + " with weights " + Arrays.toString(weightVec) + ": " + e.getMessage());
                }
            }

        }

        System.out.println("--------------------------------------------------------------");
        // Print the results
        for (Map.Entry<String, Map<String, VaryWeightsResult>> entry : results.entrySet()) {
            String weightID = entry.getKey();
            System.out.println("Results for weight vector: " + weightID);
            for (Map.Entry<String, VaryWeightsResult> algorithmEntry : entry.getValue().entrySet()) {
                String algorithm = algorithmEntry.getKey();
                VaryWeightsResult result = algorithmEntry.getValue();
                System.out.println(result.getCSVString().strip());
            }
            System.out.println();
        }

        return resultsPath;
    }

    private static void setupAlgorithmExecutor(LightweightFlowOptimizer algorithmExecutor,
                                               OptimizationResourcesBundle bundle,
                                               ExecutorService executorService) {
        // Setting up the algorithm executor (necessary but useless stuff)
        try {
            final Logger logger = Logger.getLogger(VaryWeightsExpMain.class.getName());
            Comparator<OptimizationPlan> costFormula = Comparator.comparingInt(o -> -o.totalCost());
            final BoundedPriorityQueue<OptimizationPlan> validPlans = new BoundedPriorityQueue<>(costFormula, 1, true);
            algorithmExecutor.setup(bundle, validPlans, null, executorService, null, logger);
        } catch (Exception e) {
            throw new RuntimeException("Error setting up the algorithm executor: " + e.getMessage(), e);
        }
    }

    /**
     * Method to gracefully shut down the running executor service
     * @param executorService The given executor service to terminate
     */
    private static void shutdownExecutor(ExecutorService executorService) {
        executorService.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being canceled
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS))
                    System.err.println("Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            executorService.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    private static OptimizationResourcesBundle getBundle(String dictionaryContent,
                                                         String networkPath,
                                                         String workflow,
                                                         int threads,
                                                         int timeout,
                                                         String pairLatsPath,
                                                         String pairLinksPath,
                                                         String datasetFile,
                                                         String iFogWorkflowPath,
                                                         String modelWorkflow,
                                                         String modelNetwork) throws IOException {
        OptimizationResourcesBundle bundle = OptimizationResourcesBundle.builder()
                .withNetwork(JSONSingleton.fromJson(Files.readString(Paths.get(networkPath)), Network.class))
                .withNewDictionary(new Dictionary(dictionaryContent))
                .withWorkflow(JSONSingleton.fromJson(Files.readString(Paths.get(workflow)), OptimizationRequest.class))
                .withStatisticsDir(null)
                .withDatasetFile(datasetFile)
                .withIntermediateDir(null)
                .withIFogNetworkPath(null)
                .withIFogWorkflowPath(iFogWorkflowPath)
                .withModelDirectory(null)
                .withModelWorkflow(modelWorkflow)
                .withModelNetwork(modelNetwork)
                .withJarPath(null)
                .withThreads(threads)
                .withTimeout(timeout)
                .build();

        if ("fitiot".equals("dagstar") || "fitiot".equals("fitiot") || "fitiot".equals("fitiot-dagstar")) {
            bundle.setPairLats(loadPairLatsFile(pairLatsPath));
            bundle.setPairLinks(loadPairLatsFile(pairLinksPath));
        }


        bundle.getStatisticsBundle().setWorkflow(workflow);
        bundle.getStatisticsBundle().setNetwork(networkPath);
        return bundle;
    }

    private static HashMap<String, Double> loadPairLatsFile(String filename) {
        HashMap<String, Double> pairHops = new HashMap<>();
        Logger.getLogger(StandaloneRunner.class.getName()).info("Loading pair latencies from " + filename + "...");
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("=", 2);
                pairHops.put(parts[0], Double.parseDouble(parts[1]));
            }
            Logger.getLogger(StandaloneRunner.class.getName()).info("Loaded " + pairHops.size() + " pair latencies.");

        } catch (FileNotFoundException e) {
            System.out.println("Pair latencies file " + filename + " not found.");
        } catch (IOException e) {
            System.out.println("Unexpected error while reading pair latencies file " + filename + ".");
        }
        return pairHops;
    }

    private static String getDictionaryContent(String dictionaryPath) throws IllegalArgumentException {
        String dictionaryContent;
        try (InputStream inputStream = StandaloneRunner.class.getResourceAsStream(dictionaryPath)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + dictionaryPath);
            }
            dictionaryContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return dictionaryContent;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Method to create the argument parser for the standalone runner
     * Contains all the available arguments along with their descriptions
     *
     * @return The created ArgumentParser object
     */
    private static ArgumentParser createArgumentParser() {
        ArgumentParser argumentParser = ArgumentParsers.newFor("optimizer").build()
                .defaultHelp(true)
                .description("Optimizes a given topology on top of a given network");

        argumentParser.addArgument("-nn", "--network-name")
                .dest("networkName")
                .setDefault("")
                .choices("7", "15", "31", "127", "196")
                .type(String.class)
                .nargs("?")
                .help("Name of the network that will be used in the optimization procedure");

        argumentParser.addArgument("-wd", "--workflow-directory")
                .dest("workflowDirectory")
                .setDefault("")
                .nargs("?")
                .type(String.class)
                .help("Directory where the workflow files are located");

        argumentParser.addArgument("-nd", "--network-directory")
                .dest("networkDirectory")
                .setDefault("")
                .nargs("?")
                .type(String.class)
                .help("Directory where the network files are located");

        argumentParser.addArgument("-dsp", "--dataset-path")
                .help(".xlsx or .json file where the stats per operator exist")
                .nargs("?")
                .setDefault("")
                .dest("datasetFile");

        return argumentParser;
    }
}
