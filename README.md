# Attention Economy Bidding Bot - Playtech Summer 2026

This repository contains a high-performance bidding bot developed for the **Playtech Summer 2026 Software Engineering Internship** PvP challenge. The bot is designed to compete in a real-time "Attention Economy" auction, aiming to maximize the score (Value/Spent ratio) while strictly adhering to simulation constraints.

## 🧠 Key Strategies & Features

### 1. High-Precision Value Estimation
The bot utilizes a sophisticated value calculation model derived from deep analysis of the competition harness.
- **Ad-to-Content Matching:** Implements precise category match tables (`VIDEO_CATEGORY_MATCH`) and demographic multipliers (`AGE_MUL`).
- **Contextual Accuracy:** Unlike basic implementations, this bot correctly maps viewer interests and age multipliers against the **video category** (rather than the bot's own category) to mirror the internal evaluation logic of the harness.

### 2. Strategic Budget Management (The 30% Rule)
To avoid the severe 30% spending penalty, the bot incorporates several safety mechanisms:
- **Target Buffer:** Aims for a 35% minimum spend (`MIN_SPEND_RATIO = 0.35`) to provide a safety margin against early termination.
- **Dynamic Urgency Factor:** As the simulation progresses, the bot calculates the required spending rate and increases bid aggressiveness if it falls behind the target.
- **Panic Mode:** A fail-safe trigger that forces aggressive bidding if the spending threshold hasn't been met in the final stages of the auction.

### 3. Real-time ROI Optimization
- **Global Multiplier Tuning:** Every 100 rounds, the bot analyzes the `Summary` feedback. It dynamically adjusts its bidding intensity (`globalMultiplier`) based on current ROI performance and market competitiveness.
- **Adaptive Start Bids:** To win tie-breakers and optimize for the Fibonacci-based pricing model, the bot fine-tunes its `startBidRatio` based on recent win/loss streaks.

## 🛠 Technical Specifications

- **Language:** Java 21 (Pure JDK).
- **Zero Dependencies:** No 3rd-party libraries (Spring, Lombok, etc.) were used, ensuring full compliance with contest rules.
- **Performance:** Optimized I/O handling to stay well within the **40ms** response time limit.
- **Memory Efficiency:** Strictly operates within the **192MB heap** limit.
- **Thread Safety:** Single-threaded execution as per competition requirements.

## 📦 Build & Execution

The project is structured as a standard Maven project.

### Build
To compile and package the bot into a JAR file:
```bash
mvn clean package