package com.rpgcore.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Per-world progression settings. The defaults are intentionally modest and
 * can be tuned by a server owner without changing player save data.
 */
public final class RpgCoreConfig {
    private static final ModConfigSpec.IntValue BASE_EXPERIENCE;
    private static final ModConfigSpec.IntValue EXPERIENCE_PER_LEVEL;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("progression");
        BASE_EXPERIENCE = builder
                .comment("RPG experience required to go from level 0 to level 1.")
                .defineInRange("baseExperience", 100, 1, 1_000_000);
        EXPERIENCE_PER_LEVEL = builder
                .comment("Additional RPG experience required for each current RPG level.")
                .defineInRange("experiencePerLevel", 25, 0, 1_000_000);
        builder.pop();

        SPEC = builder.build();
    }

    private RpgCoreConfig() {
    }

    /**
     * Returns the RPG experience needed to advance from the supplied level.
     * Level 0 uses the base requirement; the requirement rises linearly.
     */
    public static long experienceRequiredForLevel(int currentLevel) {
        int safeLevel = Math.max(0, currentLevel);
        return BASE_EXPERIENCE.get() + (long) EXPERIENCE_PER_LEVEL.get() * safeLevel;
    }
}
