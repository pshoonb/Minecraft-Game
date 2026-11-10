package com.rpgcore.attributes;

import net.minecraft.util.RandomSource;

/**
 * Centralized cumulative attribute formulas.
 *
 * <p>Each user formula is the marginal gain from the next allocated point.
 * The total effect is therefore the sum of f(1) through f(points). The
 * summation uses exact values for early points and small midpoint intervals
 * for very large allocations, avoiding a loop over billions of points.</p>
 */
public final class AttributeFormulaCalculator {
    private static final long EXACT_SUM_LIMIT = 8_192L;
    private static final long APPROXIMATION_INTERVAL_DIVISOR = 1_000L;
    private static final double DEFENSE_SOFT_CAP_PERCENT = 80.0;

    private static final int[] MINERAL_BASE_WEIGHTS = {
            150, 145, 140, 135, 130, 125, 120, 115,
            110, 105, 100, 95, 90, 85, 80
    };

    private AttributeFormulaCalculator() {
    }

    /** Strength: sum of 2 / x, in vanilla Minecraft damage points. */
    public static double strengthDamage(long points) {
        return cumulative(points, x -> 2.0 / x);
    }

    /**
     * Defense: sum of min(3, 20 / x) percentage points, smoothly mapped to
     * an asymptotic 80% maximum. The mapping avoids a hard cutoff.
     */
    public static double defenseReductionPercent(long points) {
        double rawPercent = cumulative(points, x -> Math.min(3.0, 20.0 / x));
        return DEFENSE_SOFT_CAP_PERCENT
                * (1.0 - Math.exp(-rawPercent / DEFENSE_SOFT_CAP_PERCENT));
    }

    /** Health: sum of max(0.01, 0.5 / ln(x + e - 1)). */
    public static double healthBonus(long points) {
        return cumulative(points, x ->
                Math.max(0.01, 0.5 / Math.log(x + Math.E - 1.0)));
    }

    /** Mining speed: cumulative percentage gain. */
    public static double miningSpeedPercent(long points) {
        return miningFormulaSum(points);
    }

    /** Movement speed: same marginal formula as mining speed, as a percentage. */
    public static double movementSpeedPercent(long points) {
        return miningFormulaSum(points);
    }

    /**
     * Jump height: each allocated point adds 0.01 + 0.49 * exp(-0.03 * x)
     * blocks, summed from x=1 through the allocated point count.
     */
    public static double jumpHeightBlocks(long points) {
        return cumulative(points, x ->
                0.01 + 0.49 * Math.exp(-0.03 * x));
    }

    /** Luck reuses the mining-speed percentage formula for mob equipment chances. */
    public static double luckEquipmentDropBonusPercent(long luckPoints) {
        return miningFormulaSum(luckPoints);
    }

    /** Experience Loot reuses the mining-speed percentage formula. */
    public static double experienceLootBonusPercent(long points) {
        return miningFormulaSum(points);
    }

    private static double miningFormulaSum(long points) {
        return cumulative(points, x ->
                5.0 / Math.log(0.1 * x + Math.E - 0.1));
    }

    /**
     * Luck flattens the agreed base distribution as it increases. At zero
     * luck the 150, 145, ... 80 weights are unchanged. A higher luck value
     * lowers the exponent, raising the relative weight of larger quantities
     * while keeping all 15 weights strictly descending for finite luck.
     */
    public static double mineralWeight(int extraMinerals, long luckPoints) {
        if (extraMinerals < 1 || extraMinerals > MINERAL_BASE_WEIGHTS.length) {
            throw new IllegalArgumentException("extraMinerals must be between 1 and 15");
        }
        if (luckPoints < 0L) {
            luckPoints = 0L;
        }

        double exponent = 100.0 / (100.0 + (double) luckPoints);
        return Math.pow(MINERAL_BASE_WEIGHTS[extraMinerals - 1], exponent);
    }

    /**
     * Selects one of 1..15 extra minerals from the dynamic weighted distribution.
     * Returns zero when the player has not allocated any Luck points.
     */
    public static int rollExtraMinerals(long luckPoints, RandomSource random) {
        if (luckPoints <= 0L) {
            return 0;
        }

        double totalWeight = 0.0;
        for (int quantity = 1; quantity <= 15; quantity++) {
            totalWeight += mineralWeight(quantity, luckPoints);
        }

        double target = random.nextDouble() * totalWeight;
        double cumulativeWeight = 0.0;
        for (int quantity = 1; quantity <= 15; quantity++) {
            cumulativeWeight += mineralWeight(quantity, luckPoints);
            if (target < cumulativeWeight) {
                return quantity;
            }
        }
        return 15;
    }

    private static double cumulative(long points, java.util.function.DoubleUnaryOperator marginal) {
        if (points <= 0L) {
            return 0.0;
        }

        double total = 0.0;
        long exactEnd = Math.min(points, EXACT_SUM_LIMIT);
        for (long x = 1L; x <= exactEnd; x++) {
            total += marginal.applyAsDouble((double) x);
        }

        if (exactEnd == points) {
            return total;
        }

        long start = exactEnd + 1L;
        while (start <= points) {
            long interval = Math.max(1L, start / APPROXIMATION_INTERVAL_DIVISOR);
            long remaining = points - start;
            long end = interval - 1L >= remaining ? points : start + interval - 1L;
            double midpoint = start + (end - start) / 2.0;
            total += marginal.applyAsDouble(midpoint) * (double) (end - start + 1L);

            if (end == points) {
                break;
            }
            start = end + 1L;
        }
        return total;
    }
}
