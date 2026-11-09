package com.rpgcore.progression;

/** Pure calculations for RPG Core progression. */
public final class RpgProgression {
    public static final int MAX_LEVEL = 100_000;
    private static final int LINEAR_REWARD_CAP = 100;

    private RpgProgression() {}

    /** Points granted when reaching targetLevel. */
    public static long pointsForUpgradeTo(int targetLevel) {
        requireValidLevel(targetLevel);
        return targetLevel / 2L + Math.min(LINEAR_REWARD_CAP, targetLevel);
    }

    /**
     * P(x) = floor(x^2/4) + x(x+1)/2, for 0 <= x <= 100
     * P(x) = floor(x^2/4) + 100x - 4950, for x > 100
     */
    public static long totalAttributePointsAtLevel(int level) {
        requireValidLevel(level);
        long x = level;
        long halfSquare = (x * x) / 4L;
        if (level <= LINEAR_REWARD_CAP) {
            return halfSquare + x * (x + 1L) / 2L;
        }
        return halfSquare + LINEAR_REWARD_CAP * x - 4_950L;
    }

    public static long pointsBetweenLevels(int oldLevel, int newLevel) {
        requireValidLevel(oldLevel);
        requireValidLevel(newLevel);
        if (newLevel < oldLevel) {
            throw new IllegalArgumentException("newLevel must not be below oldLevel");
        }
        return totalAttributePointsAtLevel(newLevel)
                - totalAttributePointsAtLevel(oldLevel);
    }

    private static void requireValidLevel(int level) {
        if (level < 0 || level > MAX_LEVEL) {
            throw new IllegalArgumentException("level out of supported range: " + level);
        }
    }
}
