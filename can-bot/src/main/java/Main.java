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
    private static final int    CAT_ID   = getCatId(CATEGORY);

    private static final double[][] VIDEO_CATEGORY_MATCH = {
            {1.0,  0.28, 0.2,  0.1,  0.25, 0.4,  0.45, 0.1,  0.19},
            {0.28, 1.0,  0.25, 0.3,  0.35, 0.19, 0.1,  0.28, 0.2 },
            {0.2,  0.25, 1.0,  0.3,  0.5,  0.28, 0.28, 0.3,  0.19},
            {0.1,  0.3,  0.3,  1.0,  0.28, 0.2,  0.25, 0.5,  0.25},
            {0.25, 0.35, 0.5,  0.28, 1.0,  0.3,  0.1,  0.19, 0.1 },
            {0.4,  0.19, 0.28, 0.2,  0.3,  1.0,  0.5,  0.25, 0.19},
            {0.45, 0.1,  0.28, 0.25, 0.1,  0.5,  1.0,  0.35, 0.1 },
            {0.1,  0.28, 0.3,  0.5,  0.19, 0.25, 0.35, 1.0,  0.28},
            {0.19, 0.2,  0.19, 0.25, 0.1,  0.19, 0.1,  0.28, 1.0 }
    };

    private static final double[][] AGE_MUL_MALE = {
            {0.45, 0.4,  0.28, 0.1,  0.6,  0.25, 0.05, 0.05, 0.05},
            {0.5,  0.5,  0.05, 0.2,  0.55, 0.2,  0.05, 0.28, 0.3 },
            {0.3,  0.45, 0.25, 0.4,  0.35, 0.1,  0.05, 0.25, 0.45},
            {0.2,  0.4,  0.45, 0.5,  0.2,  0.05, 0.05, 0.3,  0.5 },
            {0.28, 0.3,  0.25, 0.45, 0.1,  0.05, 0.05, 0.35, 0.4 },
            {0.28, 0.2,  0.2,  0.35, 0.05, 0.05, 0.05, 0.4,  0.3 }
    };
    private static final double[][] AGE_MUL_FEMALE = {
            {0.5,  0.28, 0.28, 0.28, 0.3,  0.5,  0.45, 0.28, 0.19},
            {0.5,  0.28, 0.19, 0.2,  0.2,  0.4,  0.55, 0.25, 0.1 },
            {0.3,  0.28, 0.4,  0.3,  0.28, 0.25, 0.45, 0.4,  0.3 },
            {0.2,  0.1,  0.5,  0.4,  0.1,  0.28, 0.35, 0.5,  0.3 },
            {0.28, 0.1,  0.3,  0.4,  0.19, 0.1,  0.25, 0.5,  0.25},
            {0.28, 0.1,  0.25, 0.3,  0.19, 0.1,  0.2,  0.5,  0.2 }
    };

    private static final double[] INTEREST_WEIGHTS = {1.0, 0.44, 0.225};

    private static final long[][] VIEW_BRACKETS = {
            {0L,          99L,           11},
            {100L,        999L,          21},
            {1_000L,      4_999L,         8},
            {5_000L,      24_999L,       32},
            {25_000L,     99_999L,       20},
            {100_000L,    499_999L,      37},
            {500_000L,    1_999_999L,    41},
            {2_000_000L,  7_999_999L,    22},
            {8_000_000L,  24_999_999L,   37},
            {25_000_000L, 74_999_999L,   14},
            {75_000_000L, Long.MAX_VALUE, 21}
    };

    private static final int    COMMENT_WEIGHT   = 15;
    private static final int    K                = 9999;
    private static final double SUBSCRIBED_BONUS = 0.17;

    // %35 hedef: erken bitiş senaryosuna karşı güvenlik marjı
    private static final double MIN_SPEND_RATIO  = 0.35;

    private static final double ROI_LOW          = 0.30;
    private static final double ROI_HIGH         = 0.60;

    // Muhafazakar tur tahmini: harness erken biterse diye 850k
    private static final long   SAFE_EXPECTED_ROUNDS = 850_000L;

    private static long   initialBudget    = 10_000_000L;
    private static long   ebucks           = 10_000_000L;
    private static long   totalSpent       = 0L;
    private static int    roundCount       = 0;
    private static int    summaryCount     = 0;
    private static double globalMultiplier = 1.0;
    private static double startBidRatio    = 0.65;
    private static int    consecutiveWins  = 0;
    private static int    consecutiveLosses= 0;

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            initialBudget = Long.parseLong(args[0].trim());
            ebucks        = initialBudget;
        }

        PrintStream out = new PrintStream(System.out, true, StandardCharsets.ISO_8859_1);
        out.println(CATEGORY);
        log("START category=%s id=%d budget=%d", CATEGORY, CAT_ID, initialBudget);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.ISO_8859_1));

        Map<String, String> fields = new LinkedHashMap<>(16);

        while (true) {
            String line = in.readLine();
            if (line == null) break;

            if (line.startsWith("S ")) {
                handleSummary(line);
                continue;
            }

            roundCount++;
            fields.clear();
            for (String pair : line.split(",")) {
                int eq = pair.indexOf('=');
                if (eq > 0) fields.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }

            double estimatedValue = estimateValue(fields);
            int[]  bid            = computeBid(estimatedValue);
            out.println(bid[0] + " " + bid[1]);

            String result = in.readLine();
            if (result == null) break;

            if (result.startsWith("S ")) {
                handleSummary(result);
            } else if (result.startsWith("W ")) {
                long spent = Long.parseLong(result.substring(2).trim());
                ebucks    -= spent;
                totalSpent += spent;
                consecutiveWins++;
                consecutiveLosses = 0;
                if (consecutiveWins >= 7) {
                    startBidRatio = Math.max(0.35, startBidRatio * 0.97);
                    consecutiveWins = 0;
                }
            } else if ("L".equals(result)) {
                consecutiveLosses++;
                consecutiveWins = 0;
                if (consecutiveLosses >= 7) {
                    startBidRatio = Math.min(0.92, startBidRatio * 1.03);
                    consecutiveLosses = 0;
                }
            }

            if (ebucks <= 0) break;
        }

        log("END spent=%d (%.1f%%) ebucks=%d rounds=%d mult=%.3f sbr=%.3f",
                totalSpent, 100.0 * totalSpent / initialBudget,
                ebucks, roundCount, globalMultiplier, startBidRatio);
    }

    private static double estimateValue(Map<String, String> fields) {
        long viewCount    = parseLong(fields.getOrDefault("video.viewCount",    "1"));
        long commentCount = parseLong(fields.getOrDefault("video.commentCount", "0"));
        int  videoCatId   = getCatId(fields.getOrDefault("video.category",      ""));

        int    baseValue = getBaseValue(viewCount);
        double comment   = (double)(K + commentCount * COMMENT_WEIGHT) / (double)(viewCount + K);
        double base      = baseValue * (1.0 + comment);

        double adMatch = Math.max(0.161, VIDEO_CATEGORY_MATCH[CAT_ID][videoCatId]);

        double viewerMul = 0.0;

        if ("Y".equals(fields.get("viewer.subscribed"))) {
            viewerMul += SUBSCRIBED_BONUS;
        }

        String interestsStr = fields.getOrDefault("viewer.interests", "");
        if (!interestsStr.isEmpty()) {
            String[] interests = interestsStr.split(";");
            int lim = Math.min(interests.length, INTEREST_WEIGHTS.length);
            for (int i = 0; i < lim; i++) {
                int interestCatId = getCatId(interests[i].trim());
                viewerMul += VIDEO_CATEGORY_MATCH[interestCatId][videoCatId] * INTEREST_WEIGHTS[i];
            }
        }

        // Harness: mulByAge[ageId][this.catId] — this.catId = videonun kategorisi
        int    ageId  = getAgeId(fields.getOrDefault("viewer.age",    "25-34"));
        boolean female = "F".equals(fields.getOrDefault("viewer.gender", "M"));
        viewerMul += (female ? AGE_MUL_FEMALE : AGE_MUL_MALE)[ageId][videoCatId];

        return Math.ceil(base * viewerMul * adMatch);
    }

    private static int[] computeBid(double estimatedValue) {
        if (ebucks <= 0) return new int[]{0, 0};

        double spendRatio = (double) totalSpent / initialBudget;

        // C maddesi: baraj altındaysak değersiz turlara da teklif ver
        if (estimatedValue < 5.0) {
            if (spendRatio < MIN_SPEND_RATIO) {
                int safe = (int) Math.min(10, ebucks);
                return new int[]{safe, safe};
            }
            int safe = (int) Math.min(1, ebucks);
            return new int[]{safe, safe};
        }

        double rawMax = estimatedValue * globalMultiplier;

        double budgetRatio = (double) ebucks / initialBudget;
        if      (budgetRatio < 0.05) rawMax *= 0.35;
        else if (budgetRatio < 0.15) rawMax *= 0.65;

        // B maddesi: roundCount kullan, muhafazakar SAFE_EXPECTED_ROUNDS ile sınırla
        if (spendRatio < MIN_SPEND_RATIO) {
            long roundsLeft = Math.max(1, SAFE_EXPECTED_ROUNDS - roundCount);
            double deficit        = (MIN_SPEND_RATIO - spendRatio) * initialBudget;
            double neededPerRound = deficit / roundsLeft;
            double progressRatio  = (double) roundCount / SAFE_EXPECTED_ROUNDS;
            double urgency        = Math.min(3.0, 1.0 + progressRatio * 2.0);
            rawMax = Math.max(rawMax, neededPerRound * urgency);

            if (progressRatio > 0.7 && spendRatio < 0.20) {
                rawMax = Math.max(rawMax, estimatedValue * 2.5);
                log("PANIC round=%d spend=%.2f deficit=%.0f", roundCount, spendRatio, deficit);
            }
        }

        int maxBid   = (int) Math.min(ebucks, Math.max(2, (long) rawMax));
        int startBid = (int) Math.min(ebucks, Math.max(1, (long)(maxBid * startBidRatio)));
        return new int[]{startBid, maxBid};
    }

    private static void handleSummary(String line) {
        summaryCount++;
        String[] parts = line.split(" ");
        if (parts.length < 3) return;
        try {
            double points = Double.parseDouble(parts[1]);
            double spent  = Double.parseDouble(parts[2]);
            if (spent == 0) return;
            double roi = points / spent;
            if (summaryCount > 5) {
                if      (roi < ROI_LOW)  globalMultiplier = Math.max(0.5, globalMultiplier * 0.88);
                else if (roi > ROI_HIGH) globalMultiplier = Math.min(3.5, globalMultiplier * 1.10);
            }
            log("S#%d roi=%.4f mult=%.3f sbr=%.3f spent=%.1f%%",
                    summaryCount, roi, globalMultiplier, startBidRatio,
                    100.0 * totalSpent / initialBudget);
        } catch (NumberFormatException e) {
            log("summary parse err: %s", line);
        }
    }

    private static String detectCategory() {
        try {
            String jarPath = Main.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI().getPath();
            String folderName = new File(jarPath).getParentFile().getName().toLowerCase();
            for (String cat : CATEGORY_NAMES) {
                if (folderName.contains(cat.toLowerCase().replace(" ", ""))) return cat;
            }
        } catch (Exception e) {
            log("detectCategory error: %s", e.getMessage());
        }
        return "DIY";
    }

    private static int getBaseValue(long viewCount) {
        for (long[] b : VIEW_BRACKETS)
            if (viewCount >= b[0] && viewCount <= b[1]) return (int) b[2];
        return 21;
    }

    static int getCatId(String name) {
        for (int i = 0; i < CATEGORY_NAMES.length; i++)
            if (CATEGORY_NAMES[i].equalsIgnoreCase(name.trim())) return i;
        return 3;
    }

    private static int getAgeId(String age) {
        for (int i = 0; i < AGE_BRACKET_NAMES.length; i++)
            if (AGE_BRACKET_NAMES[i].equals(age)) return i;
        return 2;
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); }
        catch (NumberFormatException e) { return 0L; }
    }

    private static void log(String fmt, Object... args) {
        System.err.printf(fmt + "%n", args);
    }
}