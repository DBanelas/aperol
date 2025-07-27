package optimizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public class VaryWeightsResult {

    private String algorithm;
    private double duration;
    private double[] weights;
    private double[] normalizedScores;
    private double totalNormalizedScore;
    private double[] denormalizedScores;


    public VaryWeightsResult() {

    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public void setWeights(double[] weights) {
        this.weights = weights;
    }

    public void setNormalizedScores(double[] normalizedScores) {
        this.normalizedScores = normalizedScores;
    }

    public void setTotalNormalizedScore(double totalNormalizedScore) {
        this.totalNormalizedScore = totalNormalizedScore;
    }

    public void setDenormalizedScores(double[] denormalizedScores) {
        this.denormalizedScores = denormalizedScores;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public double getDuration() {
        return duration;
    }

    /** Prints a readable summary of the object’s contents. */
    public void print() {
        System.out.println("Algorithm: " + (algorithm != null ? algorithm : "null"));
        System.out.println("Weights: " + (weights != null ? Arrays.toString(weights) : "null"));
        System.out.println("Normalized Scores: " + (normalizedScores != null ? Arrays.toString(normalizedScores) : "null"));
        System.out.println("Total Normalized Score: " + totalNormalizedScore);
        System.out.println("Denormalized Scores: " + (denormalizedScores != null ? Arrays.toString(denormalizedScores) : "null"));
    }




    /* ---------- CSV output ---------- */

    public String getCSVString() {
        // Build CSV line
        return String.join(",",
                csvEscape(arrayAsBrackets(weights)),
                csvEscape(algorithm),
                String.valueOf(duration),
                csvEscape(arrayAsBrackets(normalizedScores)),
                String.valueOf(totalNormalizedScore),
                csvEscape(arrayAsBrackets(denormalizedScores))
        ) + "\n";
    }

    /**
     * Writes one CSV row representing this object.
     * Each array is written as "[x, y, z]" and the whole field is quoted,
     * so embedded commas do not break the CSV.
     *
     * @param path   destination file
     * @param append true → append, false → overwrite
     * @throws IOException if a write fails
     */
    public void writeToCsv(Path path, boolean append) throws IOException {
        boolean fileExists = Files.exists(path);

        // Write header if needed (new file or overwrite)
        if (!fileExists || !append) {
            String header = "algorithm,weights,normalizedScores,totalNormalizedScore,denormalizedScores\n";
            Files.writeString(
                    path,
                    header,
                    StandardOpenOption.CREATE,
                    append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING
            );
        }

        String csvLine = getCSVString();

        Files.writeString(path, csvLine,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /** Converts a double[] into "[x.xx, y.yy]" (two-decimal format) or "[]" if null/empty. */
    private static String arrayAsBrackets(double[] a) {
        if (a == null) return "[]";
        return "[" + Arrays.stream(a)
                .mapToObj(VaryWeightsResult::formatDouble)
                .collect(Collectors.joining(", ")) + "]";
    }

    /** Formats a single double with exactly two decimals using US locale for the decimal point. */
    private static String formatDouble(double d) {
        return String.format(Locale.US, "%.2f", d);
    }

    /** Minimal CSV escaping: wrap field in double quotes and double any embedded quotes. */
    private static String csvEscape(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
