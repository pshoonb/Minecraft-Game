package com.rpgcore.experience;

import java.math.BigInteger;

/** Pure calculations for the Experience Loot attribute. */
public final class ExperienceLootCalculator {
    /**
     * Initial balance value: each allocated point grants two percentage points.
     * This remains a provisional gameplay-balance value.
     */
    public static final int PERCENT_PER_POINT = 2;

    /**
     * ExperienceOrb stores its Value as a signed short in entity save data.
     * Keep custom values positive after a world save/reload.
     */
    public static final int MAX_PERSISTABLE_ORB_VALUE = Short.MAX_VALUE;

    // Vanilla ExperienceOrb.getExperienceValue() denomination ladder, largest first.
    private static final int[] VANILLA_ORB_DENOMINATIONS = {
            2477, 1237, 617, 307, 149, 73, 37, 17, 7, 3, 1
    };

    private static final BigInteger ONE_HUNDRED = BigInteger.valueOf(100L);
    private static final BigInteger PERCENT_PER_POINT_BIG =
            BigInteger.valueOf(PERCENT_PER_POINT);
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
     * Applies the user's per-batch rule:
     * P = floor(S * (1 + 2% * points))
     * Q = P // (max(2, N) - 1)
     *
     * Every one of the N logical orbs is then created with denomination Q.
     * Q is capped to the largest positive signed-short value so it remains
     * valid when Minecraft saves and reloads the orb entity.
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

        BigInteger multiplier = ONE_HUNDRED.add(
                BigInteger.valueOf(experienceLootPoints)
                        .multiply(PERCENT_PER_POINT_BIG));

        BigInteger boostedTotal = BigInteger.valueOf(totalExperience)
                .multiply(multiplier)
                .divide(ONE_HUNDRED);

        long divisor = Math.max(2L, originalOrbCount) - 1L;
        BigInteger orbValue = boostedTotal.divide(BigInteger.valueOf(divisor));

        return orbValue.min(MAX_ORB_VALUE).intValue();
    }
}
