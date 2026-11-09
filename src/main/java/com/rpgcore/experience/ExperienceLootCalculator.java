package com.rpgcore.experience;

import java.math.BigInteger;

/** Pure calculation for the Experience Loot attribute. */
public final class ExperienceLootCalculator {
    /**
     * Initial balance value: each allocated point grants two percentage points.
     * Keep this in one place so the rate can be tuned after gameplay testing.
     */
    public static final int PERCENT_PER_POINT = 2;

    private static final BigInteger ONE_HUNDRED = BigInteger.valueOf(100L);
    private static final BigInteger PERCENT_PER_POINT_BIG =
            BigInteger.valueOf(PERCENT_PER_POINT);
    private static final BigInteger MAX_EXPERIENCE =
            BigInteger.valueOf(Integer.MAX_VALUE);

    private ExperienceLootCalculator() {
    }

    /**
     * Adds the proportional bonus to the current XP drop and rounds down.
     * BigInteger keeps very large attribute values from overflowing.
     */
    public static int applyBonus(int currentExperience, long experienceLootPoints) {
        if (currentExperience <= 0) {
            return 0;
        }
        if (experienceLootPoints <= 0L) {
            return currentExperience;
        }

        BigInteger bonusPercent = BigInteger.valueOf(experienceLootPoints)
                .multiply(PERCENT_PER_POINT_BIG);
        BigInteger result = BigInteger.valueOf(currentExperience)
                .multiply(ONE_HUNDRED.add(bonusPercent))
                .divide(ONE_HUNDRED);

        return result.min(MAX_EXPERIENCE).intValue();
    }
}
