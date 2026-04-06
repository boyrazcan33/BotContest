import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class Main {

    private static final String[] CATEGORY_NAMES = {
            "Music", "Sports", "Kids", "DIY", "Video Games", "ASMR", "Beauty", "Cooking", "Finance"
    };

    private static final String[] AGE_BRACKET_NAMES = {"13-17", "18-24", "25-34", "35-44", "45-54", "55+"};

    private static final String CATEGORY = detectCategory();
    private static final int CAT_ID = getCatId(CATEGORY);

    // VIDEO_CATEGORY_MATCH[viewerCat][DIY=3] - how well each viewer interest matches DIY ads
    private static final double[] INTEREST_TO_AD = {
            0.1,   // Music
            0.3,   // Sports
            0.3,   // Kids
            1.0,   // DIY
            0.28,  // Video Games
            0.2,   // ASMR
            0.25,  // Beauty
            0.5,   // Cooking
            0.25   // Finance
    };

    // adMatch for each video category when our bot is DIY
    // VIDEO_CATEGORY_MATCH[DIY=3][videoCat] = row 3
    private static final double[] AD_MATCH_BY_VIDEO_CAT = {
            0.1,   // Music video
            0.3,   // Sports video
            0.3,   // Kids video
            1.0,   // DIY video
            0.28,  // Video Games video
            0.2,   // ASMR video
            0.25,  // Beauty video
            0.5,   // Cooking video
            0.25   // Finance video
    };

    // AGE_CATEGORY_MULTIPLIER_MALE[age][DIY=3]
    private static final double[] AGE_MUL_MALE_DIY   = {0.1, 0.2, 0.4, 0.5, 0.45, 0.35};
    // AGE_CATEGORY_MULTIPLIER_FEMALE[age][DIY=3]
    private static final double[] AGE_MUL_FEMALE_DIY = {0.28, 0.2, 0.3, 0.4, 0.4, 0.3};

    // INTEREST_POSITION_WEIGHTS
    private static final double[] INTEREST_WEIGHTS = {1.0, 0.44, 0.225};

    // ViewBracket baseValues by viewCount range
    // {min, max, baseValue}
    private static final long[][] VIEW_BRACKETS = {
            {0,        99,       11},
            {100,      999,      21},
            {1000,     4999,      8},
            {5000,     24999,    32},
            {25000,    99999,    20},
            {100000,   499999,   37},
            {500000,   1999999,  41},
            {2000000,  7999999,  22},
            {8000000,  24999999, 37},
            {25000000, 74999999, 14},
            {75000000, Long.MAX_VALUE, 21}
    };

    private static final int COMMENT_WEIGHT = 15;
    private static final int K = 9999;
    private static final double SUBSCRIBED_BONUS = 0.17;

    private static final double ROI_LOW  = 0.30;
    private static final double ROI_HIGH = 0.45;
    private static final double BUDGET_LOW    = 0.20;
    private static final double BUDGET_DANGER = 0.05;

    private static int    initialBudget    = 10_000_000;
    private static long   ebucks           = 10_000_000;
    private static double globalMultiplier = 1.0;
    private static int    summaryCount     = 0;

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            initialBudget = (int) Long.parseLong(args[0]);
            ebucks        = initialBudget;
        }

        PrintStream out = new PrintStream(System.out, true, StandardCharsets.ISO_8859_1);
        out.println(CATEGORY);
        log("category detected: %s", CATEGORY);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.ISO_8859_1));

        Map<String, String> fields = new LinkedHashMap<>();

        while (ebucks > 0) {
            String line = in.readLine();
            if (line == null) break;

            if (line.startsWith("S ")) {
                handleSummary(line);
                continue;
            }

            fields.clear();
            for (String pair : line.split(",")) {
                int eq = pair.indexOf('=');
                if (eq > 0) fields.put(pair.substring(0, eq), pair.substring(eq + 1));
            }

            double estimatedValue = estimateValue(fields);
            int[] bid = computeBid(estimatedValue);
            out.println(bid[0] + " " + bid[1]);

            String result = in.readLine();
            if (result != null && result.startsWith("S ")) {
                handleSummary(result);
            } else if (result != null && result.startsWith("W ")) {
                int spent = Integer.parseInt(result.substring(2).trim());
                ebucks -= spent;
            }
        }

        log("done | ebucks=%d globalMult=%.2f", ebucks, globalMultiplier);
    }

    private static double estimateValue(Map<String, String> fields) {
        // 1. baseValue from viewCount bracket
        long viewCount    = parseLong(fields.getOrDefault("video.viewCount", "1"));
        long commentCount = parseLong(fields.getOrDefault("video.commentCount", "0"));
        int baseValue = getBaseValue(viewCount);

        // 2. comment multiplier — same formula as valueFor
        double comment = (double)(K + commentCount * COMMENT_WEIGHT) / (double)(viewCount + K);
        double base = baseValue * (1.0 + comment);

        // 3. adMatch — based on video category
        String videoCat = fields.getOrDefault("video.category", "");
        int videoCatId = getCatId(videoCat);
        double adMatch = Math.max(0.161, AD_MATCH_BY_VIDEO_CAT[videoCatId]);

        // 4. viewerMul
        double viewerMul = 0.0;

        // subscribed
        if ("Y".equals(fields.get("viewer.subscribed"))) {
            viewerMul += SUBSCRIBED_BONUS;
        }

        // interests
        String interests = fields.getOrDefault("viewer.interests", "");
        String[] interestArr = interests.isEmpty() ? new String[0] : interests.split(";");
        for (int i = 0; i < interestArr.length && i < INTEREST_WEIGHTS.length; i++) {
            int interestCatId = getCatId(interestArr[i].trim());
            double r = INTEREST_TO_AD[interestCatId];
            viewerMul += r * INTEREST_WEIGHTS[i];
        }

        // age multiplier
        String age    = fields.getOrDefault("viewer.age", "");
        String gender = fields.getOrDefault("viewer.gender", "M");
        int ageId = getAgeId(age);
        double ageMul = "F".equals(gender) ? AGE_MUL_FEMALE_DIY[ageId] : AGE_MUL_MALE_DIY[ageId];
        viewerMul += ageMul;

        double estimated = Math.ceil(base * viewerMul * adMatch);
        return estimated;
    }

    private static int[] computeBid(double estimatedValue) {
        // %2 alt limit kontrolü
        if (ebucks < initialBudget * 0.02) {
            return new int[]{1, 1};
        }

        // Değersiz tur kontrolü
        if (estimatedValue < 5.0) {
            return new int[]{1, 1};
        }

        double budgetRatio = (double) ebucks / initialBudget;

        double budgetFactor;
        if (budgetRatio < BUDGET_DANGER) {
            budgetFactor = 0.3;
        } else if (budgetRatio < BUDGET_LOW) {
            budgetFactor = 0.6;
        } else {
            budgetFactor = 1.0;
        }

        double effective = estimatedValue * globalMultiplier * budgetFactor;

        int maxBid = (int) effective;
        maxBid = (int) Math.max(2, Math.min(ebucks, maxBid));

        int startBid = (int)(maxBid * 0.85);
        startBid = (int) Math.max(1, Math.min(ebucks, startBid));

        return new int[]{startBid, maxBid};
    }

    private static void handleSummary(String line) {
        summaryCount++;
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
                globalMultiplier *= 0.80;
            } else if (roi > ROI_HIGH) {
                globalMultiplier *= 1.10;
            }

            globalMultiplier = Math.max(0.2, Math.min(3.0, globalMultiplier));

        } catch (NumberFormatException e) {
            log("summary parse error: %s", line);
        }
    }

    private static String detectCategory() {
        try {
            String jarPath = Main.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath();

            log("jarPath: %s", jarPath);

            String folderName = new File(jarPath).getParentFile().getName().toLowerCase();

            log("folderName: %s", folderName);

            for (String cat : CATEGORY_NAMES) {
                if (folderName.contains(cat.toLowerCase().replace(" ", ""))) {
                    return cat;
                }
            }
        } catch (Exception e) {
            log("detectCategory error: %s", e.getMessage());
        }
        return "DIY"; // default
    }

    private static int getBaseValue(long viewCount) {
        for (long[] bracket : VIEW_BRACKETS) {
            if (viewCount >= bracket[0] && viewCount <= bracket[1]) {
                return (int) bracket[2];
            }
        }
        return 21;
    }

    static int getCatId(String name) {
        for (int i = 0; i < CATEGORY_NAMES.length; i++) {
            if (CATEGORY_NAMES[i].equalsIgnoreCase(name)) return i;
        }
        return 3; // default DIY
    }

    private static int getAgeId(String age) {
        for (int i = 0; i < AGE_BRACKET_NAMES.length; i++) {
            if (AGE_BRACKET_NAMES[i].equals(age)) return i;
        }
        return 2; // default 25-34
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void log(String fmt, Object... args) {
        System.err.printf(fmt + "%n", args);
    }
}