package com.rpgcore.progression;

import com.rpgcore.data.ModAttachments;
import com.rpgcore.data.PlayerAttribute;
import com.rpgcore.data.PlayerRpgData;
import com.rpgcore.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;

/** Server-side operations for level-linked RPG attributes. */
public final class RpgProgressionService {
    private RpgProgressionService() {}

    /** Keep the displayed RPG level equal to the player's vanilla experience level. */
    public static void syncVanillaLevel(ServerPlayer player) {
        syncVanillaLevel(player, player.experienceLevel);
    }

    /**
     * LevelChange is fired while giveExperienceLevels is processing the change,
     * so the event delta is applied to the current level before synchronizing.
     */
    public static void syncVanillaLevel(ServerPlayer player, int vanillaLevel) {
        PlayerRpgData current = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
        PlayerRpgData updated = current.progressionInitialized()
                ? current.withVanillaLevel(vanillaLevel)
                : current.initializeProgression(vanillaLevel);

        if (!updated.equals(current)) {
            player.setData(ModAttachments.PLAYER_RPG_DATA.get(), updated);
            NetworkHandler.sendSnapshot(player);
        }

        RpgGameplayEffects.apply(player, updated);
    }

    /** Rebuilds transient modifiers from the player's authoritative saved data. */
    public static void refreshGameplayEffects(ServerPlayer player) {
        PlayerRpgData current = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
        RpgGameplayEffects.apply(player, current);
    }

    public static void allocateAttribute(ServerPlayer player, PlayerAttribute attribute, int amount) {
        if (attribute == null || amount < 1 || amount > 100) return;
        syncVanillaLevel(player);
        PlayerRpgData current = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
        if (current.availableAttributePoints() < amount) return;

        PlayerRpgData updated = current.addAttribute(attribute, amount);
        player.setData(ModAttachments.PLAYER_RPG_DATA.get(), updated);
        RpgGameplayEffects.apply(player, updated);
        NetworkHandler.sendSnapshot(player);
    }

    public static void resetAttributes(ServerPlayer player) {
        syncVanillaLevel(player);
        PlayerRpgData current = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
        PlayerRpgData updated = current.resetAttributes();
        player.setData(ModAttachments.PLAYER_RPG_DATA.get(), updated);
        RpgGameplayEffects.apply(player, updated);
        NetworkHandler.sendSnapshot(player);
    }
}
