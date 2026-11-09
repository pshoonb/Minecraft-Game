package com.rpgcore.progression;

/**
 * Pure calculations for RPG Core's level-based attribute-point rewards.
 *
 * <p>The player starts at level 0 with 0 points. Reaching level {@code x}
 * grants {@code floor(x / 2) + min(100, x)} points. The cumulative formula
 * lets the mod calculate a player's expected total without replaying every
 * historical level-up event.</p>
 *
 * <p>This class only calculates point totals. It does not read or modify
 * Minecraft player data and does not apply gameplay attribute effects.</p>
 */
public final class RpgProgression {
    private static final int LINEAR_REWARD_CAP = 100;

    private RpgProgression() {
    }

    /**
     * Returns the points awarded when a player reaches {@code targetLevel}.
     *
     * @param targetLevel the level reached after the level-up (level 1 starts
     *                    the progression and grants 1 point)
     * @return the reward for reaching that level
     * @throws IllegalArgumentException if {@code targetLevel} is negative
     */
    public static long pointsForUpgradeTo(int targetLevel) {
        requireNonNegativeLevel(targetLevel);

        return (targetLevel / 2L) + Math.min(LINEAR_REWARD_CAP, targetLevel);
    }

    /**
     * Returns the cumulative attribute points a player should have at the
     * given level.
     *
     * <p>For levels 0 through 100:</p>
     * <pre>
     * P(x) = floor(x² / 4) + x(x + 1) / 2
     * </pre>
     *
     * <p>For levels above 100:</p>
     * <pre>
     * P(x) = floor(x² / 4) + 100x - 4950
     * </pre>
     *
     * <p>Intermediate arithmetic uses {@code long} so squaring an
     * {@code int}-sized level does not overflow.</p>
     *
     * @param level the player's current level
     * @return cumulative points at that level
     * @throws IllegalArgumentException if {@code level} is negative
     */
    public static long totalAttributePointsAtLevel(int level) {
        requireNonNegativeLevel(level);

        long x = level;
        long halfLevelSum = (x * x) / 4L;

        if (level <= LINEAR_REWARD_CAP) {
            return halfLevelSum + (x * (x + 1L)) / 2L;
        }

        return halfLevelSum + (LINEAR_REWARD_CAP * x) - 4_950L;
    }

    /**
     * Returns the points that should be awarded between two levels.
     *
     * <p>This is useful when initializing data for a player who joins a
     * world after RPG Core has already been installed, or when reconciling
     * a stored reward baseline with the player's current level.</p>
     *
     * @param oldLevel the level already accounted for
     * @param newLevel the level now reached
     * @return the cumulative-point difference between the two levels
     * @throws IllegalArgumentException if either level is negative, or if
     *                                  {@code newLevel} is below
     *                                  {@code oldLevel}
     */
    public static long pointsBetweenLevels(int oldLevel, int newLevel) {
        requireNonNegativeLevel(oldLevel);
        requireNonNegativeLevel(newLevel);

        if (newLevel < oldLevel) {
            throw new IllegalArgumentException("newLevel must not be below oldLevel");
        }

        return totalAttributePointsAtLevel(newLevel)
                - totalAttributePointsAtLevel(oldLevel);
    }

    private static void requireNonNegativeLevel(int level) {
        if (level < 0) {
            throw new IllegalArgumentException("level must not be negative");
        }
    }
}
