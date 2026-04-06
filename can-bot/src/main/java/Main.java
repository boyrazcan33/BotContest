import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class Main {

    // Category we advertise on - fixed for the whole run
    private static final String CATEGORY = "DIY";

    // Starting bid range before any multipliers
    private static final int BASE_MIN = 5;
    private static final int BASE_MAX = 60;

    // Budget thresholds (as ratio of initial budget)
    private static final double BUDGET_LOW    = 0.20; // 20% remaining - be careful
    private static final double BUDGET_DANGER = 0.05; // 5% remaining - be very careful

    // ROI thresholds for adjusting global multiplier every 100 rounds
    private static final double ROI_LOW  = 0.15; // too low  -> we are overbidding
    private static final double ROI_HIGH = 0.40; // too high -> we can afford to bid more

    // Runtime state
    private static int    initialBudget    = 10_000_000;
    private static int    ebucks           = 10_000_000;
    private static double globalMultiplier = 1.0; // adjusted after each summary

    // Win cost tracking for opponent estimation
    private static long winCount   = 0;
    private static long winCostSum = 0;

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            initialBudget = (int) Long.parseLong(args[0]);
            ebucks        = initialBudget;
        }

        PrintStream out = new PrintStream(System.out, true, StandardCharsets.ISO_8859_1);
        out.println(CATEGORY);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.ISO_8859_1));

        Map<String, String> fields = new LinkedHashMap<>();

        while (ebucks > 0) {
            String line = in.readLine();
            if (line == null) break;

            // Summary arrives every 100 rounds: "S {points} {ebucks}"
            if (line.startsWith("S ")) {
                handleSummary(line);
                continue;
            }

            // Parse comma-separated key=value fields
            fields.clear();
            for (String pair : line.split(",")) {
                int eq = pair.indexOf('=');
                if (eq > 0) fields.put(pair.substring(0, eq), pair.substring(eq + 1));
            }

            // Compute a score for this video/viewer combination
            double score = computeScore(fields);

            // Turn score into a concrete bid
            int[] bid = computeBid(score);
            out.println(bid[0] + " " + bid[1]);

            // Read win or lose
            String result = in.readLine();
            if (result != null && result.startsWith("W ")) {
                int spent = Integer.parseInt(result.substring(2).trim());
                ebucks -= spent;
                winCount++;
                winCostSum += spent;
            }
        }

        log("done | ebucks=%d wins=%d avgWinCost=%.1f globalMult=%.2f",
                ebucks, winCount,
                winCount > 0 ? (double) winCostSum / winCount : 0,
                globalMultiplier);
    }

    // Returns a score multiplier for this round
    private static double computeScore(Map<String, String> f) {
        double score = 1.0;

        // Category match boosts value significantly
        if (CATEGORY.equals(f.get("video.category"))) {
            score *= 1.8;
        }

        // Viewer interests: first interest = strongest match
        String interests = f.getOrDefault("viewer.interests", "");
        String[] interestArr = interests.isEmpty() ? new String[0] : interests.split(";");
        if (interestArr.length > 0 && CATEGORY.equals(interestArr[0].trim())) {
            score *= 1.5; // top interest - highest relevance
        } else if (interests.contains(CATEGORY)) {
            score *= 1.2; // in list but not first
        }

        // Subscribed viewers generate more value
        if ("Y".equals(f.get("viewer.subscribed"))) {
            score *= 1.3;
        }

        // Engagement ratio: high comments/views = niche engaged audience
        double engagement = computeEngagement(
                f.getOrDefault("video.viewCount",    "1"),
                f.getOrDefault("video.commentCount", "0"));
        score *= engagementMultiplier(engagement);

        // Age: DIY audience peaks at 25-44
        score *= ageMultiplier(f.getOrDefault("viewer.age", ""));

        // 3 interests = more niche viewer
        if (interestArr.length == 3) {
            score *= 1.1;
        }

        return score;
    }

    private static double computeEngagement(String viewStr, String commentStr) {
        try {
            long views    = Long.parseLong(viewStr.trim());
            long comments = Long.parseLong(commentStr.trim());
            if (views == 0) return 0;
            return (double) comments / views;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Maps engagement ratio to a multiplier
    private static double engagementMultiplier(double ratio) {
        if (ratio >= 0.05) return 2.0; // very niche, very valuable
        if (ratio >= 0.02) return 1.6;
        if (ratio >= 0.01) return 1.3;
        if (ratio >= 0.005) return 1.1;
        if (ratio >= 0.001) return 1.0;
        return 0.7; // inflated view count, low real engagement
    }

    // DIY content resonates most with working-age adults
    private static double ageMultiplier(String age) {
        switch (age) {
            case "25-34": return 1.25;
            case "35-44": return 1.20;
            case "18-24": return 1.05;
            case "45-54": return 0.95;
            case "55+":   return 0.85;
            case "13-17": return 0.80;
            default:      return 1.0;
        }
    }

    // Converts score into [startBid, maxBid]
    private static int[] computeBid(double score) {
        double budgetRatio = (double) ebucks / initialBudget;

        // Slow down spending as budget shrinks
        double budgetFactor;
        if (budgetRatio < BUDGET_DANGER) {
            budgetFactor = 0.3;
        } else if (budgetRatio < BUDGET_LOW) {
            budgetFactor = 0.6;
        } else {
            budgetFactor = 1.0;
        }

        // Use observed average win cost as our baseline once we have enough data
        double baseline = winCount > 50
                ? (double) winCostSum / winCount
                : BASE_MAX;

        double effective = score * globalMultiplier * budgetFactor;

        // maxBid: go slightly above average win cost when score is high
        int maxBid = (int) (baseline * effective * 1.1);
        maxBid = Math.max(1, Math.min(ebucks, maxBid));

        // startBid: close to maxBid to win tie-breakers (protocol: higher startBid wins ties)
        int startBid = (int) (maxBid * 0.85);
        startBid = Math.max(1, Math.min(ebucks, startBid));

        return new int[]{startBid, maxBid};
    }

    // Adjusts globalMultiplier based on ROI from last 100 rounds
    private static void handleSummary(String line) {
        // Format: "S {points} {ebucks}"
        String[] parts = line.split(" ");
        if (parts.length < 3) return;

        try {
            double points = Double.parseDouble(parts[1]);
            double spent  = Double.parseDouble(parts[2]);
            if (spent == 0) return;

            double roi = points / spent;
            log("summary | roi=%.4f globalMult=%.2f", roi, globalMultiplier);

            if (roi < ROI_LOW) {
                globalMultiplier *= 0.90; // overbidding, pull back
            } else if (roi > ROI_HIGH) {
                globalMultiplier *= 1.10; // room to be more aggressive
            }

            // Keep multiplier in a sane range
            globalMultiplier = Math.max(0.3, Math.min(3.0, globalMultiplier));

        } catch (NumberFormatException e) {
            log("summary parse error: %s", line);
        }
    }

    private static void log(String fmt, Object... args) {
        System.err.printf(fmt + "%n", args);
    }
}