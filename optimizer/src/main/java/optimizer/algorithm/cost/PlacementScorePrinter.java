package optimizer.algorithm.cost;

import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;

public class PlacementScorePrinter {
    public static AsciiTable at = new AsciiTable();
    static int count = 0;

    static {
        at.addRule();
        at.addRow("Placement         ", "metrics score", "migration cost", "final score");
        at.setTextAlignment(TextAlignment.CENTER);
        at.addRule();
    }

    public static void addData(String signature, double metricsScore, double migrationCost, int finalScore) {
        at.addRow(signature, metricsScore, migrationCost, finalScore);
        at.setTextAlignment(TextAlignment.CENTER);
        if (++count % 20 == 0) {
            at.addRule();
        }

    }

    public static void print() {
        String rend = at.render(160);
        System.out.println(rend);
    }
}
