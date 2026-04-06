import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class Main {

    // Category we advertise on - fixed for the whole run
    private static final String CATEGORY = "DIY";

    // Base bid range - fixed, not derived from past wins
    private static final int BASE_MIN = 3;
    private static final int BASE_MAX = 50;

    // Junk video threshold - if score is below this, bid 1 1
    private static final double JUNK_THRESHOLD = 0.8;

    // Engagement ratio threshold below which a non-category video is junk
    private static final double JUNK_ENGAGEMENT = 0.005;

    // ROI thresholds for adjusting global multiplier every 100 rounds
    private static final double ROI_LOW  = 0.28; // overbidding - cut hard
    private static final double ROI_HIGH = 0.45; // room to be more aggressive

    // Budget thresholds
    private static final double BUDGET_LOW    = 0.20;
    private static final double BUDGET_DANGER = 0.05;

    // Runtime state
    private static int    initialBudget    = 10_000_000;
    private static int    ebucks           = 10_000_000;
    private static double globalMultiplier = 1.0;

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

            // Sniper mode: junk videos get minimum bid
            if (isJunk(score, fields)) {
                out.println("1 1");
            } else {
                int[] bid = computeBid(score);
                out.println(bid[0] + " " + bid[1]);
            }

            // Read win or lose
            String result = in.readLine();
            if (result != null && result.startsWith("W ")) {
                int spent = Integer.parseInt(result.substring(2).trim());
                ebucks -= spent;
            }
        }

        log("done | ebucks=%d globalMult=%.2f", ebucks, globalMultiplier);
    }

    // Returns true if this video is not worth real money
    private static boolean isJunk(double score, Map<String, String> fields) {
        // Not our category AND low engagement = junk
        boolean categoryMatch = CATEGORY.equals(fields.get("video.category"));
        double engagement = computeEngagement(
                fields.getOrDefault("video.viewCount",    "1"),
                fields.getOrDefault("video.commentCount", "0"));

        if (!categoryMatch && engagement < JUNK_ENGAGEMENT) return true;

        // Score below threshold = not worth bidding real money
        return score < JUNK_THRESHOLD;
    }

    // Returns a score multiplier for this round
    private static double computeScore(Map<String, String> fields) {
        double score = 1.0;

        // Category match boosts value significantly
        if (CATEGORY.equals(fields.get("video.category"))) {
            score *= 1.8;
        }

        // Viewer interests: first interest = strongest match
        String interests = fields.getOrDefault("viewer.interests", "");
        String[] interestArr = interests.isEmpty() ? new String[0] : interests.split(";");
        if (interestArr.length > 0 && CATEGORY.equals(interestArr[0].trim())) {
            score *= 1.5;
        } else if (interests.contains(CATEGORY)) {
            score *= 1.2;
        }

        // Subscribed viewers generate more value
        if ("Y".equals(fields.get("viewer.subscribed"))) {
            score *= 1.3;
        }

        // Engagement ratio: high comments/views = niche engaged audience
        double engagement = computeEngagement(
                fields.getOrDefault("video.viewCount",    "1"),
                fields.getOrDefault("video.commentCount", "0"));
        score *= engagementMultiplier(engagement);

        // Age: DIY audience peaks at 25-44
        score *= ageMultiplier(fields.getOrDefault("viewer.age", ""));

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

    private static double engagementMultiplier(double ratio) {
        if (ratio >= 0.05) return 2.0;
        if (ratio >= 0.02) return 1.6;
        if (ratio >= 0.01) return 1.3;
        if (ratio >= 0.005) return 1.1;
        if (ratio >= 0.001) return 1.0;
        return 0.7;
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

        double budgetFactor;
        if (budgetRatio < BUDGET_DANGER) {
            budgetFactor = 0.3;
        } else if (budgetRatio < BUDGET_LOW) {
            budgetFactor = 0.6;
        } else {
            budgetFactor = 1.0;
        }

        double effective = score * globalMultiplier * budgetFactor;

        int maxBid = (int) (BASE_MAX * effective);
        maxBid = Math.max(2, Math.min(ebucks, maxBid));

        // startBid close to maxBid to win tie-breakers
        int startBid = (int) (maxBid * 0.85);
        startBid = Math.max(1, Math.min(ebucks, startBid));

        return new int[]{startBid, maxBid};
    }

    private static int summaryCount = 0;


    // Adjusts globalMultiplier based on ROI from last 100 rounds
    private static void handleSummary(String line) {
        summaryCount++;
        // warm-up period: observe only, no adjustment
        if (summaryCount <= 5) return;
        String[] parts = line.split(" ");
        if (parts.length < 3) return;

        try {
            double points = Double.parseDouble(parts[1]);
            double spent  = Double.parseDouble(parts[2]);
            if (spent == 0) return;

            double roi = points / spent;
            log("summary | roi=%.4f globalMult=%.2f", roi, globalMultiplier);

            if (roi < ROI_LOW) {
                globalMultiplier *= 0.75; // overbidding - cut hard
            } else if (roi > ROI_HIGH) {
                globalMultiplier *= 1.10; // room to be more aggressive
            }

            globalMultiplier = Math.max(0.2, Math.min(3.0, globalMultiplier));

        } catch (NumberFormatException e) {
            log("summary parse error: %s", line);
        }
    }

    private static void log(String fmt, Object... args) {
        System.err.printf(fmt + "%n", args);
    }
}