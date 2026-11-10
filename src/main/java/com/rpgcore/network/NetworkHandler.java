package com.rpgcore.network;

import com.rpgcore.data.ModAttachments;
import com.rpgcore.data.PlayerAttribute;
import com.rpgcore.data.PlayerRpgData;
import com.rpgcore.progression.RpgProgressionService;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class NetworkHandler {
    private NetworkHandler() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(RequestRpgDataPayload.TYPE, RequestRpgDataPayload.STREAM_CODEC,
                NetworkHandler::handleRequest);
        registrar.playToServer(AllocateAttributePayload.TYPE, AllocateAttributePayload.STREAM_CODEC,
                NetworkHandler::handleAllocate);
        registrar.playToServer(ResetAttributesPayload.TYPE, ResetAttributesPayload.STREAM_CODEC,
                NetworkHandler::handleReset);
        registrar.playToServer(UnlockTeleportPayload.TYPE, UnlockTeleportPayload.STREAM_CODEC,
                NetworkHandler::handleUnlockTeleport);
        registrar.playToServer(RequestTeleportTargetsPayload.TYPE, RequestTeleportTargetsPayload.STREAM_CODEC,
                NetworkHandler::handleRequestTeleportTargets);
        registrar.playToServer(TeleportToPlayerPayload.TYPE, TeleportToPlayerPayload.STREAM_CODEC,
                NetworkHandler::handleTeleportToPlayer);
    }

    private static void handleRequest(RequestRpgDataPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            RpgProgressionService.syncVanillaLevel(player);
            sendSnapshot(player);
        }
    }

    private static void handleAllocate(AllocateAttributePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        PlayerAttribute attribute = PlayerAttribute.byNetworkId(payload.attributeId());
        RpgProgressionService.changeAttribute(player, attribute, payload.amount());
    }

    private static void handleReset(ResetAttributesPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            RpgProgressionService.resetAttributes(player);
        }
    }

    private static void handleUnlockTeleport(UnlockTeleportPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) RpgProgressionService.unlockTeleport(player);
    }

    private static void handleRequestTeleportTargets(RequestTeleportTargetsPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) sendTeleportTargets(player);
    }

    private static void handleTeleportToPlayer(TeleportToPlayerPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            RpgProgressionService.teleportToPlayer(player, payload.targetId());
        }
    }

    public static void sendTeleportTargets(ServerPlayer player) {
        PlayerRpgData data = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
        if (!data.teleportUnlocked()) {
            PacketDistributor.sendToPlayer(player, new TeleportTargetsPayload(List.of()));
            return;
        }
        List<TeleportTargetsPayload.Target> targets = new ArrayList<>();
        for (ServerPlayer onlinePlayer : player.serverLevel().getServer().getPlayerList().getPlayers()) {
            if (!onlinePlayer.getUUID().equals(player.getUUID())) {
                targets.add(new TeleportTargetsPayload.Target(
                        onlinePlayer.getUUID(), onlinePlayer.getGameProfile().getName()));
            }
        }
        targets.sort(Comparator.comparing(
                TeleportTargetsPayload.Target::playerName, String.CASE_INSENSITIVE_ORDER));
        PacketDistributor.sendToPlayer(player, new TeleportTargetsPayload(targets));
    }

    public static void sendSnapshot(ServerPlayer player) {
        PlayerRpgData data = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
        PacketDistributor.sendToPlayer(player, new SyncRpgDataPayload(player.getUUID(), data));
    }
}
