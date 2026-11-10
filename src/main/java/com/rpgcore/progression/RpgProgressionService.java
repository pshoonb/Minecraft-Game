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

    /**
     * Applies a signed attribute adjustment. Positive values allocate available
     * points; negative values refund points from that attribute, clamped at zero.
     */
    public static void changeAttribute(ServerPlayer player, PlayerAttribute attribute, int amount) {
        if (attribute == null || amount == 0 || amount < -100 || amount > 100) return;

        syncVanillaLevel(player);
        PlayerRpgData current = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
        PlayerRpgData updated;

        if (amount > 0) {
            int allocatedAmount = (int) Math.min((long) amount, current.availableAttributePoints());
            if (allocatedAmount <= 0) return;
            updated = current.addAttribute(attribute, allocatedAmount);
        } else {
            long requestedRefund = -(long) amount;
            long refundableAmount = Math.min(requestedRefund, current.valueOf(attribute));
            if (refundableAmount <= 0L) return;
            updated = current.removeAttribute(attribute, refundableAmount);
        }

        if (updated.equals(current)) return;
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
