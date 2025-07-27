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
    import optimizer.algorithm.*;
    import optimizer.algorithm.FlowOptimizer;
    import optimizer.algorithm.cost.OperatorCostEstimatorInterface;
    import optimizer.algorithm.cost.OperatorDagStarCostEstimator;
    import optimizer.algorithm.newalgs.competitors.DAGStar;
    import optimizer.plan.OptimizationPlan;

    import java.io.BufferedReader;
    import java.io.FileNotFoundException;
    import java.io.IOException;
    import java.io.InputStream;
    import java.nio.charset.StandardCharsets;
    import java.nio.file.Files;
    import java.nio.file.Paths;
    import java.time.Duration;
    import java.time.Instant;
    import java.util.*;
    import java.util.concurrent.ExecutorService;
    import java.util.concurrent.Executors;
    import java.util.concurrent.Flow;
    import java.util.concurrent.TimeUnit;
    import java.util.concurrent.atomic.AtomicInteger;
    import java.util.logging.Logger;

    public final class StandaloneRunner {
        public static void main(String[] args) {
            ArgumentParser argumentParser = createArgumentParser();
            Namespace ns = argumentParser.parseArgsOrFail(args);

            String workflowPath = ns.getString("workflowPath");
            String iFogWorkflowPath = ns.getString("iFogWorkflowPath");
            String networkPath = ns.getString("networkPath");
            String iFogNetworkPath = ns.getString("iFogNetworkPath");
            String jarPath = ns.getString("jarPath");
            String algorithmName = ns.getString("algorithmName");
            int timeout = ns.getInt("timeout");
            int threads = ns.getInt("threads");
            String pairLatsPath = ns.getString("pairLatsPath");
            String pairLinksPath = ns.getString("pairLinksPath");
            String intermediatePath = ns.getString("intermediateDir");
            String scoreCalculationMethod = ns.getString("scoreCalculationMethod");
            String modelNetwork = ns.getString("modelNetwork");
            String modelWorkflow = ns.getString("modelWorkflow");
            boolean disableStats = ns.getBoolean("disableStats");
            double percentage = ns.getDouble("percentage");
            int batchSize = ns.getInt("batchSize");
            int numHops = ns.getInt("numHops");
            int numIterations = ns.getInt("numIterations");
            boolean isSimDataset = ns.getBoolean("isSimDataset");

            String costPath = ns.getString("costPath");
            String modelDirectory = costPath;
            String datasetFile = costPath;
            String distributionDirectory = costPath;


            if (isSimDataset) {
                if (modelWorkflow.equals("yahoo-benchmark")) {
                    throw new IllegalArgumentException("The 'yahoo-benchmark' workflow is not supported for simulation datasets.");
                }
            }

            if (algorithmName.equals("dagstar") || algorithmName.equals("governor") || algorithmName.equals("spring-relax")) {
                if (scoreCalculationMethod.equals("model") || scoreCalculationMethod.equals("dist")) {
                    throw new IllegalArgumentException("DAGStar algorithm does not support model or distribution" +
                            "score calculation methods. Use latency instead.");
                }
            }


            if (scoreCalculationMethod.equals("model")) {
                if (modelWorkflow.isEmpty()) {
                    throw new IllegalArgumentException("Model's workflow name must be set when model is chosen " +
                            "as the score evaluation method.");
                }
                if (modelDirectory.isEmpty()) {
                    throw new IllegalArgumentException("Models' directory must be set when model is chosen " +
                            "as the score evaluation method.");
                }
                if (modelNetwork.isEmpty()) {
                    throw new IllegalArgumentException("Model's network name must be set when model is chosen " +
                            "as the score evaluation method.");
                }
            }

            if (scoreCalculationMethod.equals("latency")) {
                if (pairLatsPath.isEmpty()) {
                    throw new IllegalArgumentException("Pair latencies file must be set when dagstar is chosen " +
                            "as the score evaluation method.");
                }

                if (datasetFile.isEmpty()) {
                    throw new IllegalArgumentException("Dataset file must be set when dagstar is chosen " +
                            "as the score evaluation method.");
                }
            }

            if (algorithmName.equals("e-qp") && percentage == -1.0) {
                throw new IllegalStateException("Quick pick algorithm requires a percentage of total plans to sample.");
            }

            try {
                String dictionaryContent = getDictionaryContent(modelWorkflow, modelNetwork);

                //Get resources
                OptimizationResourcesBundle bundle = getBundle(dictionaryContent, networkPath,
                        workflowPath, threads, timeout, scoreCalculationMethod, pairLatsPath,
                        pairLinksPath, null, datasetFile,
                        jarPath, intermediatePath, iFogNetworkPath, iFogWorkflowPath,
                        distributionDirectory, modelDirectory, modelWorkflow, modelNetwork);

                //Extract and load
                final Map<String, Double> pairLats = bundle.getPairLats();
                OperatorCostEstimatorInterface operatorDagStarCostEstimator = new OperatorDagStarCostEstimator(datasetFile, pairLats, isSimDataset);

                final Logger logger = Logger.getLogger(StandaloneRunner.class.getName());
                //Select the algorithm
                final BoundedPriorityQueue<OptimizationPlan> validPlans;
                final GraphTraversalAlgorithm gta;
                final ExecutorService executorService = Executors.newFixedThreadPool(threads);
                //Comparators
                Comparator<OptimizationPlan> costFormula = Comparator.comparingInt(o -> -o.totalCost());

                //Algo specific params
                switch (algorithmName) {
                    case "e-gsp":
                    case "e-esq":
                    case "e-hsp":
                    case "e-escp":
                    case "e-qp":
                    case "e-bescp":
                    case "hybrid":
                    case "spring-relax":
                    case "governor":
                        gta = new FlowOptimizer(algorithmName, percentage, batchSize,
                                scoreCalculationMethod, false, isSimDataset,false,
                                numIterations, numHops, disableStats);
                        validPlans = new BoundedPriorityQueue<>(costFormula, 1, true);
                        break;
                    case "dagstar":
                        gta = new DAGStar(DAGStar.AggregationStrategy.MAX, true);
                        validPlans = new BoundedPriorityQueue<>(costFormula, 1, true);
                        break;
                    default:
                        throw new IllegalStateException("Supported algorithms are: [e-gsp, e-esq, e-hsp, e-escp, e-qp, dagstar, hybrid, governor, spring-relax, e-bescp]");
                }

                //Execute the algorithm
                Instant startInstant = Instant.now();
                // There is no need to pass a root plan since it is created inside the FlowOptimizer's doWork() method.
                gta.setup(bundle, validPlans, null, executorService, operatorDagStarCostEstimator, logger);
                bundle.getStatisticsBundle().setSetupDuration(Duration.between(startInstant, Instant.now()).toMillis());

                gta.doWork();
                bundle.getStatisticsBundle().setExecDuration(Duration.between(startInstant, Instant.now()).toMillis());

                gta.teardown();

                //Check if any plans were produced
                if (validPlans.isEmpty()) {
                    logger.warning("Optimizer failed to produce any valid plans.");
                    System.exit(0);
                }

                OptimizationPlan bestPlan = validPlans.peek();

                //Log results
                bundle.getStatisticsBundle().setCost(bestPlan.totalCost());
                bundle.getStatisticsBundle().setAlgorithm(algorithmName);
                bundle.setNumOfPlans(new AtomicInteger(1));
                bundle.getStatisticsBundle().setTotalThreads(threads);

                if (algorithmName.equals("dagstar")) {
                    FlowOptimizer check = new FlowOptimizer(algorithmName, percentage, batchSize,
                            "latency", false, isSimDataset,true,
                            numIterations, numHops, disableStats);
                    check.setDagStarOptimal(bestPlan.getOperatorsAndImplementations());
                    check.setup(bundle, validPlans, null, executorService, operatorDagStarCostEstimator, logger);
                    check.doWork();
                    check.teardown();
                }

                shutdownExecutor(executorService);
            } catch (Exception e) {
                System.out.println("Unexpected exception: " + e);
            }
        }


        private static String getDictionaryContent(String workflowName, String networkName) throws IllegalArgumentException {
            String dictionaryPath;
            if (workflowName.contains("yahoo"))
                dictionaryPath = "/yahoo/dict_" + networkName + "_1_" + workflowName + "-ifogsim" + ".json";
            else
                dictionaryPath = "/riot/dict_" + networkName + "_1_riot-" + workflowName + "-ifogsim" + ".json";

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
         * Method to gracefully shut down the running executor service
         * @param executorService The given executor service to terminate
         */
        private static void shutdownExecutor(ExecutorService executorService) {
            executorService.shutdown(); // Disable new tasks from being submitted
            try {
                // Wait a while for existing tasks to terminate
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow(); // Cancel currently executing tasks
                    // Wait a while for tasks to respond to being cancelled
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

        /**
         * Method to parse the pair hops file and create the pair hops map
         * @param filename The name of the pair hops file
         * @return returns a HashMap of the pair hops
         */
        private static HashMap<String, Integer> loadPairHopsFile(String filename) {
            HashMap<String, Integer> pairHops = new HashMap<>();
            Logger.getLogger(StandaloneRunner.class.getName()).info("Loading pair hops from " + filename + "...");
            try (BufferedReader reader = Files.newBufferedReader(Paths.get(filename))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("=", 2);
                    pairHops.put(parts[0], Integer.parseInt(parts[1]));
                }
                Logger.getLogger(StandaloneRunner.class.getName()).info("Loaded " + pairHops.size() + " pair hops.");

            } catch(FileNotFoundException e){
                System.out.println("Pair hops file " + filename + " not found.");
            } catch (IOException e) {
                System.out.println("Unexpected error while reading pair hops file " + filename + ".");
            }
            return pairHops;
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

            } catch(FileNotFoundException e){
                System.out.println("Pair latencies file " + filename + " not found.");
            } catch (IOException e) {
                System.out.println("Unexpected error while reading pair latencies file " + filename + ".");
            }
            return pairHops;
        }



        //The optimizer bundle is a list of resources necessary for algorithms to run

        /**
         * Method to store all necessary resources for the algorithm to run into a bundle object.
         * Contains literally everything: paths, numThreads ...
         */
        private static OptimizationResourcesBundle getBundle(String dictionaryContent,
                                                             String networkPath,
                                                             String workflow,
                                                             int threads,
                                                             int timeout,
                                                             String costEstimationMethod,
                                                             String pairLatsPath,
                                                             String pairLinksPath,
                                                             String statisticsDir,
                                                             String datasetFile,
                                                             String jarPath,
                                                             String intermediateDir,
                                                             String iFogNetworkPath,
                                                             String iFogWorkflowPath,
                                                             String distributionDirectory,
                                                             String modelDirectory,
                                                             String modelWorkflow,
                                                             String modelNetwork) throws IOException {
            OptimizationResourcesBundle bundle = OptimizationResourcesBundle.builder()
                    .withNetwork(JSONSingleton.fromJson(Files.readString(Paths.get(networkPath)), Network.class))
                    .withNewDictionary(new Dictionary(dictionaryContent))
                    .withWorkflow(JSONSingleton.fromJson(Files.readString(Paths.get(workflow)), OptimizationRequest.class))
                    .withStatisticsDir(statisticsDir)
                    .withDatasetFile(datasetFile)
                    .withIntermediateDir(intermediateDir)
                    .withIFogNetworkPath(iFogNetworkPath)
                    .withIFogWorkflowPath(iFogWorkflowPath)
                    .withDistributionsDirectory(distributionDirectory)
                    .withModelDirectory(modelDirectory)
                    .withModelWorkflow(modelWorkflow)
                    .withModelNetwork(modelNetwork)
                    .withJarPath(jarPath)
                    .withThreads(threads)
                    .withTimeout(timeout)
                    .build();

            if (costEstimationMethod.equals("latency")) {
                bundle.setPairLats(loadPairLatsFile(pairLatsPath));
                bundle.setPairLinks(loadPairLatsFile(pairLinksPath));
            }

            bundle.getStatisticsBundle().setWorkflow(workflow);
            bundle.getStatisticsBundle().setNetwork(networkPath);
            return bundle;
        }

        /**
         * Method to create the argument parser for the standalone runner
         * Contains all the available arguments along with their descriptions
         * @return The created ArgumentParser object
         */
        private static ArgumentParser createArgumentParser() {
            ArgumentParser argumentParser = ArgumentParsers.newFor("optimizer").build()
                    .defaultHelp(true)
                    .description("Optimizes a given topology on top of a given network");

            argumentParser.addArgument("--disable-live-stats")
                    .dest("disableStats")
                    .action(Arguments.storeTrue())
                    .type(Boolean.class)
                    .setDefault(false)
                    .nargs("?")
                    .help("Flag to denote whether the optimizer should print statistics of the " +
                            "running algorithm in real time");

            argumentParser.addArgument("-cp", "--cost-path")
                    .dest("costPath")
                    .nargs("?")
                    .type(String.class)
                    .help("File of the costs that will be used.");

            argumentParser.addArgument("-ccm", "--cost-calculation-method")
                    .dest("scoreCalculationMethod")
                    .choices("dist", "model", "latency")
                    .setDefault("dist")
                    .nargs("?")
                    .help("Flag to denote whether the optimizer should the distributions," +
                            "the models, or the dag-star method to calculate the cost of a plan");

            argumentParser.addArgument("-wfn", "--workflow-name")
                    .dest("modelWorkflow")
                    .choices("train", "stats", "pred", "etl", "yahoo-benchmark")
                    .setDefault("")
                    .type(String.class)
                    .nargs("?")
                    .help("Workflow name so that the optimizer knows which model to choose");

            argumentParser.addArgument("-nn", "--network-name")
                    .dest("modelNetwork")
                    .setDefault("")
                    .choices("7", "15", "31", "127", "194", "1023", "2047")
                    .type(String.class)
                    .nargs("?")
                    .help("Network size so that the optimizer knows which model to choose");

            argumentParser.addArgument("-wp", "--workflow-path")
                    .dest("workflowPath")
                    .nargs("?")
                    .help("Path to the workflow file");

            argumentParser.addArgument("-iwp", "--ifogsim-workflow-path")
                    .dest("iFogWorkflowPath")
                    .nargs("?")
                    .setDefault("")
                    .help("Path to the ifogsim format workflow file");

            argumentParser.addArgument("-dp", "--dictionary-path")
                    .dest("dictionaryPath")
                    .nargs("?")
                    .help("Path to the dictionary file");

            argumentParser.addArgument("-hp", "--pairhops-path")
                    .help("Path to the file where the pair hops are")
                    .nargs("?")
                    .dest("pairHopsPath");

            argumentParser.addArgument("-lp", "--pairlats-path")
                    .help("Path to the file where the pair latencies are")
                    .nargs("?")
                    .dest("pairLatsPath");

            argumentParser.addArgument("-pl", "--pairlinks-path")
                    .help("Path to the file where the pair links necessary for the reconstruction of the network are")
                    .nargs("?")
                    .dest("pairLinksPath");

            argumentParser.addArgument("-np", "--network-path")
                    .dest("networkPath")
                    .nargs("?")
                    .help("Path to the network file");

            argumentParser.addArgument("-inp", "--ifogsim-network-path")
                    .dest("iFogNetworkPath")
                    .nargs("?")
                    .setDefault("")
                    .help("Path to the ifogsim format network file");

            argumentParser.addArgument("-a", "--algorithm")
                    .dest("algorithmName")
                    .nargs("?")
                    .help("The name of the algorithm to execute");

            argumentParser.addArgument("-pc", "--percentage")
                    .dest("percentage")
                    .type(Double.class)
                    .setDefault(-1.0)
                    .nargs("?")
                    .help("Percentage of total plans to use for quick pick algorithm");

            argumentParser.addArgument("-t", "--timeout")
                    .dest("timeout")
                    .setDefault(-1)
                    .nargs("?")
                    .type(Integer.class)
                    .help("Timeout for the algorithm in milliseconds");

            argumentParser.addArgument("-p", "--parallelism")
                    .dest("threads")
                    .setDefault(4)
                    .nargs("?")
                    .type(Integer.class)
                    .help("Number of threads to use for the algorithm");

            argumentParser.addArgument("-bs", "--batch-size")
                    .dest("batchSize")
                    .type(Integer.class)
                    .setDefault(10_000)
                    .nargs("?")
                    .help("The batch size for the algorithm");

            argumentParser.addArgument("-nh", "--num-hops")
                    .dest("numHops")
                    .type(Integer.class)
                    .setDefault(2)
                    .nargs("?")
                    .help("The number of hops away from the root plan that the quick pick algorithm " +
                            "is allowed to search");

            argumentParser.addArgument("-ni", "--num-iterations")
                    .dest("numIterations")
                    .type(Integer.class)
                    .setDefault(1)
                    .nargs("?")
                    .help("Number of times to run the chosen algorithm in order to " +
                            "output the cumulative gain compared to the root plan");

            argumentParser.addArgument("--isSimDataset")
                    .dest("isSimDataset")
                    .action(Arguments.storeTrue())
                    .type(Boolean.class)
                    .setDefault(false)
                    .nargs("?")
                    .help("Flag to denote whether the dataset is a simulation dataset or not. " +
                            "If true, the dataset is a simulation dataset, otherwise it is a real dataset.");

            return argumentParser;
        }

    }
