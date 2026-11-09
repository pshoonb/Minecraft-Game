package com.rpgcore.progression;

import com.rpgcore.config.RpgCoreConfig;
import com.rpgcore.data.ModAttachments;
import com.rpgcore.data.PlayerAttribute;
import com.rpgcore.data.PlayerRpgData;
import com.rpgcore.network.NetworkHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Server-side API for RPG XP, level progression and attribute allocation. */
public final class RpgProgressionService {
    private RpgProgressionService() {}

    /**
     * One-time migration for players who already existed before this feature.
     * Their current vanilla level becomes their initial RPG level; future RPG
     * XP is tracked separately and never consumes or changes vanilla XP.
     */
    public static void initializeIfNeeded(ServerPlayer player) {
        PlayerRpgData data = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
        if (!data.progressionInitialized()) {
            player.setData(ModAttachments.PLAYER_RPG_DATA.get(),
                    data.initializeProgression(player.experienceLevel));
        }
    }

    public static void addExperience(ServerPlayer player, long amount) {
        if (amount <= 0L) return;
        initializeIfNeeded(player);
        PlayerRpgData current = player.getData(ModAttachments.PLAYER_RPG_DATA.get());

        long accepted = Math.min(amount, 1_000_000L);
        long totalXp = saturatedAdd(current.totalExperience(), accepted);
        long remaining = accepted;
        long progress = current.experienceProgress();
        int level = current.rpgLevel();

        while (level < RpgProgression.MAX_LEVEL && remaining > 0L) {
            long required = RpgCoreConfig.experienceRequiredForLevel(level);
            long needed = Math.max(0L, required - progress);
            if (needed == 0L) {
                level++;
                progress = 0L;
            } else if (remaining < needed) {
                progress += remaining;
                remaining = 0L;
            } else {
                remaining -= needed;
                level++;
                progress = 0L;
            }
        }
        if (level >= RpgProgression.MAX_LEVEL) progress = 0L;

        player.setData(ModAttachments.PLAYER_RPG_DATA.get(),
                current.withProgression(level, progress, totalXp));
        if (level > current.rpgLevel()) {
            player.displayClientMessage(
                    Component.literal("RPG Level Up! Level " + level), true);
        }
        NetworkHandler.sendSnapshot(player);
    }

    public static void allocateAttribute(ServerPlayer player, PlayerAttribute attribute, int amount) {
        if (attribute == null || amount < 1 || amount > 100) return;
        initializeIfNeeded(player);
        PlayerRpgData current = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
        if (current.availableAttributePoints() < amount) return;

        player.setData(ModAttachments.PLAYER_RPG_DATA.get(), current.addAttribute(attribute, amount));
        NetworkHandler.sendSnapshot(player);
    }

    public static void resetAttributes(ServerPlayer player) {
        initializeIfNeeded(player);
        PlayerRpgData current = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
        player.setData(ModAttachments.PLAYER_RPG_DATA.get(), current.resetAttributes());
        NetworkHandler.sendSnapshot(player);
    }

    private static long saturatedAdd(long a, long b) {
        return b > 0L && a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }
}
