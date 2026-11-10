package com.rpgcore.progression;

import com.rpgcore.attributes.PlayerAttributeEffects;
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
    }

    public static void allocateAttribute(ServerPlayer player, PlayerAttribute attribute, int amount) {
        if (attribute == null || amount < 1 || amount > 100) return;
        syncVanillaLevel(player);
        PlayerRpgData current = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
        int allocatedAmount = (int) Math.min((long) amount, current.availableAttributePoints());
        if (allocatedAmount <= 0) return;

        PlayerRpgData updated = current.addAttribute(attribute, allocatedAmount);
        player.setData(ModAttachments.PLAYER_RPG_DATA.get(), updated);
        PlayerAttributeEffects.apply(player, updated);
        NetworkHandler.sendSnapshot(player);
    }

    public static void resetAttributes(ServerPlayer player) {
        syncVanillaLevel(player);
        PlayerRpgData updated = player.getData(ModAttachments.PLAYER_RPG_DATA.get()).resetAttributes();
        player.setData(ModAttachments.PLAYER_RPG_DATA.get(), updated);
        PlayerAttributeEffects.apply(player, updated);
        NetworkHandler.sendSnapshot(player);
    }
}
