package com.rpgcore.progression;

import com.rpgcore.attributes.PlayerAttributeEffects;
import com.rpgcore.data.ModAttachments;
import com.rpgcore.data.PlayerAttribute;
import com.rpgcore.data.PlayerRpgData;
import com.rpgcore.network.NetworkHandler;
import com.rpgcore.teleport.TeleportRequestManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;

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
        PlayerAttributeEffects.apply(player, updated, current);
        NetworkHandler.sendSnapshot(player);
    }

    /** Unlocks teleport permanently after reaching level 30 at least once and paying 1,000 points. */
    public static void unlockTeleport(ServerPlayer player) {
        syncVanillaLevel(player);
        PlayerRpgData current = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
        if (current.teleportUnlocked()) {
            NetworkHandler.sendSnapshot(player);
            return;
        }
        if (current.highestLevelReached() < 30) {
            player.displayClientMessage(Component.translatable("message.rpgcore.teleport.level_required"), true);
            return;
        }
        if (current.availableAttributePoints() < PlayerRpgData.TELEPORT_UNLOCK_COST) {
            player.displayClientMessage(Component.translatable(
                    "message.rpgcore.teleport.not_enough_points", current.availableAttributePoints()), true);
            return;
        }
        PlayerRpgData updated = current.withTeleportUnlocked();
        player.setData(ModAttachments.PLAYER_RPG_DATA.get(), updated);
        NetworkHandler.sendSnapshot(player);
        player.displayClientMessage(Component.translatable("message.rpgcore.teleport.unlock_success"), true);
    }

    /**
     * Validates the selected target on the server. A target who has unlocked
     * TP must explicitly approve the request; otherwise the teleport is immediate.
     */
    public static void teleportToPlayer(ServerPlayer player, UUID targetId) {
        PlayerRpgData requesterData = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
        if (!requesterData.teleportUnlocked()) {
            showTeleportFailure(player, "message.rpgcore.teleport.locked");
            return;
        }
        if (targetId == null || targetId.equals(player.getUUID())) {
            showTeleportFailure(player, "message.rpgcore.teleport.target_invalid");
            return;
        }

        MinecraftServer server = player.getServer();
        ServerPlayer target = server == null ? null : server.getPlayerList().getPlayer(targetId);
        if (target == null || target.isRemoved() || target.getServer() != server) {
            showTeleportFailure(player, "message.rpgcore.teleport.target_invalid");
            return;
        }

        PlayerRpgData targetData = target.getData(ModAttachments.PLAYER_RPG_DATA.get());
        if (targetData.teleportUnlocked()) {
            TeleportRequestManager.request(player, target);
            return;
        }

        completeTeleport(player, target);
    }

    /**
     * Completes a teleport after any required consent has been obtained.
     * This method revalidates both players on the server immediately before use.
     */
    public static boolean completeTeleport(ServerPlayer player, ServerPlayer target) {
        MinecraftServer server = player.getServer();
        if (server == null
                || server.getPlayerList().getPlayer(player.getUUID()) != player
                || target == null
                || target.isRemoved()
                || target.getServer() != server
                || server.getPlayerList().getPlayer(target.getUUID()) != target) {
            showTeleportFailure(player, "message.rpgcore.teleport.target_invalid");
            return false;
        }

        PlayerRpgData requesterData = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
        if (!requesterData.teleportUnlocked()) {
            showTeleportFailure(player, "message.rpgcore.teleport.locked");
            return false;
        }

        player.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(),
                Set.of(), target.getYRot(), target.getXRot());
        player.displayClientMessage(Component.translatable(
                "message.rpgcore.teleport.success", target.getGameProfile().getName())
                .withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    /** Send a red Action Bar message for a failed teleport operation. */
    public static void showTeleportFailure(ServerPlayer player, String translationKey, Object... args) {
        player.displayClientMessage(
                Component.translatable(translationKey, args).withStyle(ChatFormatting.RED), true);
    }

    public static void resetAttributes(ServerPlayer player) {
        syncVanillaLevel(player);
        PlayerRpgData current = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
        PlayerRpgData updated = current.resetAttributes();
        player.setData(ModAttachments.PLAYER_RPG_DATA.get(), updated);
        PlayerAttributeEffects.apply(player, updated, current);
        NetworkHandler.sendSnapshot(player);
    }
}
