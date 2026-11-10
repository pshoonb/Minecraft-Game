package com.rpgcore.attributes;

import net.minecraft.util.RandomSource;

/**
 * Centralized cumulative attribute formulas.
 *
 * <p>Each user formula is the marginal gain from the next allocated point.
 * Where the discrete sum has a practical closed form, it is evaluated
 * directly. The remaining reciprocal-logarithm sums keep the existing
 * bounded numerical summation/large-range approximation.</p>
 */
public final class AttributeFormulaCalculator {
    private static final long EXACT_SUM_LIMIT = 8_192L;
    private static final long APPROXIMATION_INTERVAL_DIVISOR = 1_000L;
    private static final double DEFENSE_SOFT_CAP_PERCENT = 80.0;
    private static final double EULER_MASCHERONI = 0.5772156649015328606;

    private static final int[] MINERAL_BASE_WEIGHTS = {
            150, 145, 140, 135, 130, 125, 120, 115,
            110, 105, 100, 95, 90, 85, 80
    };

    private AttributeFormulaCalculator() {
    }

    /**
     * Strength: sum(2 / x) = 2 * H_points, where H_n is the nth harmonic
     * number. The harmonic number is evaluated in bounded time.
     */
    public static double strengthDamage(long points) {
        return points <= 0L ? 0.0 : 2.0 * harmonicNumber(points);
    }

    /**
     * Defense raw gain is sum(min(3, 20 / x)).
     *
     * <p>For points <= 6 this is exactly 3 * points. For points > 6 it is
     * 18 + 20 * (H_points - H_6). The existing asymptotic 80% soft-cap
     * mapping is applied after this equivalent closed-form sum.</p>
     */
    public static double defenseReductionPercent(long points) {
        if (points <= 0L) {
            return 0.0;
        }

        double rawPercent = points <= 6L
                ? 3.0 * points
                : 18.0 + 20.0 * (harmonicNumber(points) - 2.45);
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
     * Jump height: sum(0.01 + 0.49 * exp(-0.03 * x)).
     *
     * <p>The exponential part is a geometric series, so the exact discrete
     * sum is 0.01*n + 0.49*q*(1-q^n)/(1-q), where q = exp(-0.03).
     * expm1 is used to avoid cancellation for small point counts.</p>
     */
    public static double jumpHeightBlocks(long points) {
        if (points <= 0L) {
            return 0.0;
        }

        double n = (double) points;
        double exponent = -0.03;
        double q = Math.exp(exponent);
        double geometricSum = q
                * -Math.expm1(exponent * n)
                / -Math.expm1(exponent);
        return 0.01 * n + 0.49 * geometricSum;
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
     * Evaluates the harmonic number H_n = sum(1/k, k=1..n).
     *
     * <p>For n < 16, direct summation is cheaper and avoids asymptotic
     * expansion error. For n >= 16, the Euler-Maclaurin expansion through
     * the n^-22 term is used. Its truncation error at the threshold is far
     * below double precision, and runtime is constant with respect to n.</p>
     */
    private static double harmonicNumber(long n) {
        if (n <= 0L) {
            return 0.0;
        }

        if (n < 16L) {
            double total = 0.0;
            for (long k = 1L; k <= n; k++) {
                total += 1.0 / (double) k;
            }
            return total;
        }

        double inverse = 1.0 / (double) n;
        double inverseSquared = inverse * inverse;
        double correction = inverse * 0.5 + inverseSquared * (
                -1.0 / 12.0 + inverseSquared * (
                        1.0 / 120.0 + inverseSquared * (
                                -1.0 / 252.0 + inverseSquared * (
                                        1.0 / 240.0 + inverseSquared * (
                                                -1.0 / 132.0 + inverseSquared * (
                                                        691.0 / 32760.0 + inverseSquared * (
                                                                -1.0 / 12.0 + inverseSquared * (
                                                                        3617.0 / 8160.0 + inverseSquared * (
                                                                                -43867.0 / 14364.0 + inverseSquared * (
                                                                                        174611.0 / 6600.0
                                                                                                - 854513.0 / 3036.0 * inverseSquared
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );

        return Math.log((double) n) + EULER_MASCHERONI + correction;
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
