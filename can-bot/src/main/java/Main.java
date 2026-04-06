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

    private static final double ROI_LOW  = 0.25;
    private static final double ROI_HIGH = 0.50;
    private static final double BUDGET_LOW    = 0.20;
    private static final double BUDGET_DANGER = 0.05;

    private static final int EXPECTED_ROUNDS = 1_000_000;
    private static final double MIN_SPEND_RATIO = 0.30;

    private static int    initialBudget    = 10_000_000;
    private static long   ebucks           = 10_000_000;
    private static double globalMultiplier = 1.0;
    private static int    summaryCount     = 0;

    private static int    consecutiveWins   = 0;
    private static int    consecutiveLosses = 0;
    private static double startBidRatio     = 0.70;

    private static long totalSpent  = 0;
    private static int  roundCount  = 0;

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            initialBudget = (int) Long.parseLong(args[0]);
            ebucks        = initialBudget;
        }

        PrintStream out = new PrintStream(System.out, true, StandardCharsets.ISO_8859_1);
        out.println(CATEGORY);
        log("category detected: %s (id=%d)", CATEGORY, CAT_ID);

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

            roundCount++;

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
                totalSpent += spent;

                consecutiveWins++;
                consecutiveLosses = 0;
                if (consecutiveWins >= 5) {
                    startBidRatio = Math.max(0.30, startBidRatio * 0.96);
                    log("startBidRatio -> %.3f (easy wins, streak=%d)", startBidRatio, consecutiveWins);
                    consecutiveWins = 0;
                }

            } else if (result != null && result.equals("L")) {
                consecutiveLosses++;
                consecutiveWins = 0;
                if (consecutiveLosses >= 5) {
                    startBidRatio = Math.min(0.95, startBidRatio * 1.04);
                    log("startBidRatio -> %.3f (competitive, streak=%d)", startBidRatio, consecutiveLosses);
                    consecutiveLosses = 0;
                }
            }
        }

        long scoreDenom = Math.max(totalSpent, (long)(initialBudget * MIN_SPEND_RATIO));
        log("done | ebucks=%d spent=%d (%.1f%%) scoreDenom=%d globalMult=%.2f startBidRatio=%.3f",
                ebucks, totalSpent, 100.0 * totalSpent / initialBudget, scoreDenom, globalMultiplier, startBidRatio);
    }

    private static double estimateValue(Map<String, String> fields) {
        long viewCount    = parseLong(fields.getOrDefault("video.viewCount", "1"));
        long commentCount = parseLong(fields.getOrDefault("video.commentCount", "0"));
        int baseValue = getBaseValue(viewCount);

        double comment = (double)(K + commentCount * COMMENT_WEIGHT) / (double)(viewCount + K);
        double base = baseValue * (1.0 + comment);

        String videoCat = fields.getOrDefault("video.category", "");
        int videoCatId = getCatId(videoCat);
        double adMatch = Math.max(0.161, VIDEO_CATEGORY_MATCH[CAT_ID][videoCatId]);

        double viewerMul = 0.0;

        if ("Y".equals(fields.get("viewer.subscribed"))) {
            viewerMul += SUBSCRIBED_BONUS;
        }

        String interests = fields.getOrDefault("viewer.interests", "");
        String[] interestArr = interests.isEmpty() ? new String[0] : interests.split(";");
        for (int i = 0; i < interestArr.length && i < INTEREST_WEIGHTS.length; i++) {
            int interestCatId = getCatId(interestArr[i].trim());
            double r = VIDEO_CATEGORY_MATCH[interestCatId][CAT_ID];
            viewerMul += r * INTEREST_WEIGHTS[i];
        }

        String age    = fields.getOrDefault("viewer.age", "");
        String gender = fields.getOrDefault("viewer.gender", "M");
        int ageId = getAgeId(age);
        double ageMul = "F".equals(gender) ? AGE_MUL_FEMALE[ageId][CAT_ID] : AGE_MUL_MALE[ageId][CAT_ID];
        viewerMul += ageMul;

        return Math.ceil(base * viewerMul * adMatch);
    }

    private static int[] computeBid(double estimatedValue) {
        long minSurvival = initialBudget / 50;
        long minRequired = (long)(initialBudget * MIN_SPEND_RATIO);

        // Only stop if we've spent enough AND hit survival threshold
        if (ebucks <= minSurvival && totalSpent >= minRequired) {
            return new int[]{0, 0};
        }

        if (estimatedValue < 5.0) {
            return new int[]{1, 1};
        }

        double budgetRatio = (double) ebucks / initialBudget;
        double spendRatio = (double) totalSpent / initialBudget;

        double budgetFactor;
        if (budgetRatio < BUDGET_DANGER) {
            budgetFactor = 0.3;
        } else if (budgetRatio < BUDGET_LOW) {
            budgetFactor = 0.6;
        } else {
            budgetFactor = 1.0;
        }

        double effective = estimatedValue * globalMultiplier * budgetFactor;

        // PANIC MODE: If critically behind on spending after 60% of rounds
        if (roundCount > EXPECTED_ROUNDS * 0.6 && spendRatio < 0.15) {
            effective = Math.max(effective, estimatedValue * 2.0);
            log("PANIC: spendRatio=%.2f round=%d forcing aggressive bids", spendRatio, roundCount);
        }
        // Late-game enforcement: start at 50% of expected rounds
        else if (roundCount > EXPECTED_ROUNDS * 0.5) {
            if (totalSpent < minRequired) {
                long deficit = minRequired - totalSpent;
                long roundsLeft = EXPECTED_ROUNDS - roundCount;
                if (roundsLeft > 0) {
                    double targetPerRound = (double) deficit / roundsLeft;
                    effective = Math.max(effective, targetPerRound * 1.5);
                }
            }
        }

        int maxBid = (int) Math.min(ebucks, (long) effective);
        maxBid = Math.max(2, maxBid);

        int startBid = (int)(maxBid * startBidRatio);
        startBid = (int) Math.min(ebucks, startBid);
        startBid = Math.max(1, startBid);

        return new int[]{startBid, maxBid};
    }

    private static void handleSummary(String line) {
        summaryCount++;

        // Longer warm-up: first 1000 rounds
        if (summaryCount <= 10) return;

        String[] parts = line.split(" ");
        if (parts.length < 3) return;

        try {
            double points = Double.parseDouble(parts[1]);
            double spent  = Double.parseDouble(parts[2]);
            if (spent == 0) return;

            double roi = points / spent;
            log("summary | roi=%.4f globalMult=%.2f startBidRatio=%.3f totalSpent=%d (%.1f%%)",
                    roi, globalMultiplier, startBidRatio, totalSpent, 100.0 * totalSpent / initialBudget);

            if (roi < ROI_LOW) {
                globalMultiplier *= 0.85;
            } else if (roi > ROI_HIGH) {
                globalMultiplier *= 1.08;
            }

            // Higher floor: don't go below 0.6
            globalMultiplier = Math.max(0.6, Math.min(3.0, globalMultiplier));

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

            String folderName = new File(jarPath).getParentFile().getName().toLowerCase();

            for (String cat : CATEGORY_NAMES) {
                if (folderName.contains(cat.toLowerCase().replace(" ", ""))) {
                    return cat;
                }
            }
        } catch (Exception e) {
            log("detectCategory error: %s", e.getMessage());
        }
        return "DIY";
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
        return 3;
    }

    private static int getAgeId(String age) {
        for (int i = 0; i < AGE_BRACKET_NAMES.length; i++) {
            if (AGE_BRACKET_NAMES[i].equals(age)) return i;
        }
        return 2;
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