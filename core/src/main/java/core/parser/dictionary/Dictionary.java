package core.parser.dictionary;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import core.parser.network.Network;
import core.parser.network.Site;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

public class Dictionary implements Serializable {
    private final Map<String, List<Integer>> costCoefficients;
    private final Map<String, Integer> inputRate;
    //Platforms
    private final Map<String, Map<String, String>> operatorPlatformOperatorNames;
    private final Map<String, Map<String, Integer>> operatorPlatformStaticCosts;
    private final Map<String, Map<String, Map<String, Integer>>> operatorPlatformMigrationCosts;
    //Sites
    private final Map<String, Map<String, Integer>> operatorSiteStaticCosts;
    private final Map<String, Map<String, Map<String, Integer>>> operatorSiteMigrationCosts;
    private String name;
    //Original input
    private String originalInput;

    public Dictionary(String input) {
        //Save the input
        this.originalInput = String.valueOf(input.toCharArray());

        //Standard JSON parser
        JsonParser jsonParser = new JsonParser();

        //Init collections
        this.costCoefficients = new HashMap<>();
        this.inputRate = new HashMap<>();
        this.operatorPlatformOperatorNames = new HashMap<>();
        this.operatorPlatformStaticCosts = new HashMap<>();
        this.operatorPlatformMigrationCosts = new HashMap<>();
        this.operatorSiteStaticCosts = new HashMap<>();
        this.operatorSiteMigrationCosts = new HashMap<>();

        JsonObject element = jsonParser.parse(input).getAsJsonObject();
        this.name = element.get("dictionaryName").getAsString();
        for (JsonElement operatorElement : element.get("operators").getAsJsonArray()) {

            //Class key for this operator
            String opClassKey = operatorElement.getAsJsonObject().get("classKey").getAsString();

            //Input rate (tuples/sec)
            int inputRate = operatorElement.getAsJsonObject().get("inputRate").getAsInt();
            this.inputRate.put(opClassKey, inputRate);

            //Cost coefficients
            this.costCoefficients.put(opClassKey, new ArrayList<>());
            operatorElement.getAsJsonObject().get("costCoefficients").getAsJsonArray().iterator().forEachRemaining(i -> this.costCoefficients.get(opClassKey).add(i.getAsInt()));

            //Platforms
            this.operatorPlatformOperatorNames.put(opClassKey, new HashMap<>());
            this.operatorPlatformStaticCosts.put(opClassKey, new HashMap<>());
            this.operatorPlatformMigrationCosts.put(opClassKey, new HashMap<>());

            Set<Map.Entry<String, JsonElement>> platformEntries = operatorElement.getAsJsonObject().get("platforms").getAsJsonObject().entrySet();
            for (Map.Entry<String, JsonElement> platformEntry : platformEntries) {
                String platformName = platformEntry.getKey();
                JsonObject platformObject = platformEntry.getValue().getAsJsonObject();

                String operatorName = platformObject.get("operatorName").getAsString();
                this.operatorPlatformOperatorNames.get(opClassKey).put(platformName, operatorName);

                int staticCost = platformObject.get("staticCost").getAsInt();
                operatorPlatformStaticCosts.get(opClassKey).put(platformName, staticCost);

                Map<String, Integer> migrationCostMap = new HashMap<>();
                Set<Map.Entry<String, JsonElement>> migrationEntries = platformObject.get("migrationCosts").getAsJsonObject().entrySet();
                for (Map.Entry<String, JsonElement> migrationEntry : migrationEntries) {
                    String migrationPlatformName = migrationEntry.getKey();
                    int migrationPlatformCost = migrationEntry.getValue().getAsInt();
                    migrationCostMap.put(migrationPlatformName, migrationPlatformCost);
                }
                this.operatorPlatformMigrationCosts.get(opClassKey).put(platformName, migrationCostMap);
            }

            //Sites
            this.operatorSiteStaticCosts.put(opClassKey, new HashMap<>());
            this.operatorSiteMigrationCosts.put(opClassKey, new HashMap<>());
            Set<Map.Entry<String, JsonElement>> siteEntries = operatorElement.getAsJsonObject().get("sites").getAsJsonObject().entrySet();
            for (Map.Entry<String, JsonElement> siteEntry : siteEntries) {
                String siteName = siteEntry.getKey();
                JsonObject siteObject = siteEntry.getValue().getAsJsonObject();

                int staticCost = siteObject.get("staticCost").getAsInt();
                this.operatorSiteStaticCosts.get(opClassKey).put(siteName, staticCost);

                Map<String, Integer> migrationCostMap = new HashMap<>();
                Set<Map.Entry<String, JsonElement>> migrationEntries = siteObject.get("migrationCosts").getAsJsonObject().entrySet();
                for (Map.Entry<String, JsonElement> migrationEntry : migrationEntries) {
                    String migrationSiteName = migrationEntry.getKey();
                    int migrationSiteCost = migrationEntry.getValue().getAsInt();
                    migrationCostMap.put(migrationSiteName, migrationSiteCost);
                }
                this.operatorSiteMigrationCosts.get(opClassKey).put(siteName, migrationCostMap);
            }
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    //Dictionary API
    public Map<String, List<String>> getImplementationsForClassKey(String operatorClassKey, Network network) {
        Map<String, List<String>> impls = new HashMap<>();
        Set<String> supportedSites = network.getSites().stream().map(Site::getSiteName).collect(Collectors.toSet());

        for (String site : this.operatorSiteStaticCosts.get(operatorClassKey).keySet()) {
            if (!supportedSites.contains(site)) {
                continue;
            }
            List<String> platforms = new ArrayList<>(this.operatorPlatformStaticCosts.get(operatorClassKey).keySet());
            impls.put(site, platforms);
        }

        return impls;
    }

    public int getOperatorCost(String classKey) {
        int inputRate = this.inputRate.get(classKey);
        int cost = 0;
        int coefficient = 0;
        for (int coefficientVal : this.costCoefficients.get(classKey)) {
            if (coefficient < 0) {
                throw new IllegalStateException("Illegal coefficient value: " + coefficientVal);
            } else if (coefficient == 0) {
                cost += coefficientVal;
            } else if (coefficient == 1) {
                cost += Math.log(inputRate) * coefficientVal;
            } else {
                cost += Math.pow(inputRate, coefficient - 1) * coefficientVal;
            }
            coefficient++;
        }
        return cost;
    }

    public int getMinSiteCostForOperator(String classKey) {
        return this.operatorSiteStaticCosts.getOrDefault(classKey, Collections.emptyMap()).entrySet().stream()
                .min(Comparator.comparingInt(Map.Entry::getValue))
                .orElseGet(() -> new AbstractMap.SimpleEntry<>(null, 0))
                .getValue();
    }

    public int getMinPlatformCostForOperator(String classKey) {
        return this.operatorPlatformStaticCosts.getOrDefault(classKey, Collections.emptyMap()).entrySet().stream()
                .min(Comparator.comparingInt(Map.Entry::getValue))
                .orElseGet(() -> new AbstractMap.SimpleEntry<>(null, 0))
                .getValue();
    }

    public int getMinPlatformCostMigrationForOperator(String classKey) {
        return this.operatorPlatformMigrationCosts.getOrDefault(classKey, Collections.emptyMap()).values().stream()
                .map(Map::values)
                .flatMap(Collection::stream)
                .min(Comparator.comparingInt(o -> o))
                .orElse(0);
    }

    public int getMinSiteCostMigrationForOperator(String classKey) {
        return this.operatorPlatformMigrationCosts.getOrDefault(classKey, Collections.emptyMap()).values().stream()
                .map(Map::values)
                .flatMap(Collection::stream)
                .min(Comparator.comparingInt(o -> o))
                .orElse(0);
    }

    public int getSiteMigrationCostForClassKey(String classKey, String fromSiteName, String toSiteName) {
//        return this.operatorSiteMigrationCosts.get(classKey).get(fromSiteName).get(toSiteName);
        return 0;
    }

    public int getPlatformMigrationCostForClassKey(String classKey, String fromPlatformName, String toPlatformName) {
//        return this.operatorPlatformMigrationCosts.get(classKey).get(fromPlatformName).get(toPlatformName);
        return 0;
    }

    public int getSiteStaticCostForClassKey(String classKey, String site) {
        return this.operatorSiteStaticCosts.get(classKey).get(site);
    }

    public int getPlatformStaticCostForClassKey(String classKey, String platform) {
        return this.operatorPlatformStaticCosts.get(classKey).get(platform);
    }


    //Stats API
    public Integer updateInputRateForOperator(String operator, int rate) {
        return this.inputRate.put(operator, rate);
    }

    public Integer updatePlatformStaticCostForOperator(String operator, String platform, int cost) {
        return this.operatorPlatformStaticCosts.get(operator).put(platform, cost);
    }

    public Integer updateSiteStaticCostForOperator(String operator, String site, int cost) {
        return this.operatorSiteStaticCosts.get(operator).put(site, cost);
    }

    public Integer updatePlatformMigrationCostForOperator(String operator, String platformFrom, String platformTo, int cost) {
        return this.operatorPlatformMigrationCosts.get(operator).get(platformFrom).put(platformTo, cost);
    }

    public Integer updateSiteMigrationCostForOperator(String operator, String siteFrom, String siteTo, int cost) {
        return this.operatorSiteMigrationCosts.get(operator).get(siteFrom).put(siteTo, cost);
    }

    public String getOriginalInput() {
        return originalInput;
    }

    @Override
    public String toString() {
        return "Dictionary{" +
                "name='" + name + '\'' +
                ", costCoefficients=" + costCoefficients +
                ", inputRate=" + inputRate +
                ", operatorPlatformOperatorNames=" + operatorPlatformOperatorNames +
                ", operatorPlatformStaticCosts=" + operatorPlatformStaticCosts +
                ", operatorPlatformMigrationCosts=" + operatorPlatformMigrationCosts +
                ", operatorSiteStaticCosts=" + operatorSiteStaticCosts +
                ", operatorSiteMigrationCosts=" + operatorSiteMigrationCosts +
                '}';
    }
}
