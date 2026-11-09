package com.rpgcore.experience;

import com.rpgcore.attributes.AttributeFormulaCalculator;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Pure calculations for the Experience Loot attribute. */
public final class ExperienceLootCalculator {
    /**
     * ExperienceOrb stores its Value as a signed short in entity save data.
     * Keep custom values positive after a world save/reload.
     */
    public static final int MAX_PERSISTABLE_ORB_VALUE = Short.MAX_VALUE;

    // Vanilla ExperienceOrb.getExperienceValue() denomination ladder, largest first.
    private static final int[] VANILLA_ORB_DENOMINATIONS = {
            2477, 1237, 617, 307, 149, 73, 37, 17, 7, 3, 1
    };

    private static final BigInteger MAX_ORB_VALUE =
            BigInteger.valueOf(MAX_PERSISTABLE_ORB_VALUE);

    private ExperienceLootCalculator() {
    }

    /**
     * Counts the logical XP orbs vanilla would split from one living-entity XP
     * award batch, before same-value orbs can merge into entity stacks.
     */
    public static long countVanillaOrbUnits(long totalExperience) {
        if (totalExperience <= 0L) {
            return 0L;
        }

        long remaining = totalExperience;
        long orbCount = 0L;
        for (int denomination : VANILLA_ORB_DENOMINATIONS) {
            orbCount += remaining / denomination;
            remaining %= denomination;
        }
        return orbCount;
    }

    /**
     * Applies the agreed per-batch rule:
     * T' = floor(T * (1 + miningFormulaSum(experienceLootPoints) / 100))
     * Q  = T' // (max(2, N) - 1)
     *
     * Every one of the N logical orbs receives denomination Q. Integer
     * division remainders are intentionally discarded.
     */
    public static int calculateOrbValue(
            long totalExperience,
            long originalOrbCount,
            long experienceLootPoints) {
        if (totalExperience <= 0L
                || originalOrbCount <= 0L
                || experienceLootPoints <= 0L) {
            return 0;
        }

        double bonusPercent =
                AttributeFormulaCalculator.experienceLootBonusPercent(experienceLootPoints);
        if (!Double.isFinite(bonusPercent) || bonusPercent <= 0.0) {
            return 0;
        }

        BigDecimal multiplier = BigDecimal.ONE.add(
                BigDecimal.valueOf(bonusPercent).movePointLeft(2));
        BigInteger boostedTotal = BigDecimal.valueOf(totalExperience)
                .multiply(multiplier)
                .setScale(0, RoundingMode.DOWN)
                .toBigInteger();

        long divisor = Math.max(2L, originalOrbCount) - 1L;
        BigInteger orbValue = boostedTotal.divide(BigInteger.valueOf(divisor));

        return orbValue.min(MAX_ORB_VALUE).intValue();
    }
}
