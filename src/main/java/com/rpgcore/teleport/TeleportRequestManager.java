package com.rpgcore.teleport;

import com.rpgcore.data.ModAttachments;
import com.rpgcore.data.PlayerRpgData;
import com.rpgcore.progression.RpgProgressionService;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.mojang.brigadier.arguments.StringArgumentType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Holds short-lived teleport consent requests on the logical server. */
public final class TeleportRequestManager {
    private static final long REQUEST_TIMEOUT_MILLIS = 60_000L;
    private static final Map<UUID, Map<UUID, PendingRequest>> REQUESTS_BY_TARGET = new HashMap<>();
    private static long nextRequestSequence;

    private TeleportRequestManager() {
    }

    private record PendingRequest(
            UUID requesterId,
            UUID targetId,
            String requesterName,
            long expiresAtMillis,
            long sequence
    ) {
    }

    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("accept")
                .executes(TeleportRequestManager::executeAcceptLatest)
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(TeleportRequestManager::suggestPendingRequesters)
                        .executes(TeleportRequestManager::executeAccept)));
        event.getDispatcher().register(Commands.literal("deny")
                .executes(TeleportRequestManager::executeDenyLatest)
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(TeleportRequestManager::suggestPendingRequesters)
                        .executes(TeleportRequestManager::executeDeny)));
    }

    private static int executeAcceptLatest(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        return acceptLatest(context.getSource().getPlayerOrException());
    }

    private static int executeAccept(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer target = context.getSource().getPlayerOrException();
        String requesterName = StringArgumentType.getString(context, "player");
        return "*".equals(requesterName) ? acceptAll(target) : accept(target, requesterName);
    }

    private static int executeDenyLatest(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        return denyLatest(context.getSource().getPlayerOrException());
    }

    private static int executeDeny(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer target = context.getSource().getPlayerOrException();
        String requesterName = StringArgumentType.getString(context, "player");
        return "*".equals(requesterName) ? denyAll(target) : deny(target, requesterName);
    }

    private static CompletableFuture<Suggestions> suggestPendingRequesters(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        ServerPlayer target = context.getSource().getPlayer();
        if (target == null) {
            return builder.buildFuture();
        }

        MinecraftServer server = target.getServer();
        if (server != null) {
            expireRequests(server, System.currentTimeMillis());
        }

        builder.suggest("*");
        for (PendingRequest pending : pendingForTarget(target.getUUID())) {
            builder.suggest(pending.requesterName());
        }
        return builder.buildFuture();
    }

    /** Creates a consent request after the caller has verified both players and TP unlock state. */
    public static void request(ServerPlayer requester, ServerPlayer target) {
        MinecraftServer server = requester.getServer();
        if (server == null
                || target.getServer() != server
                || server.getPlayerList().getPlayer(target.getUUID()) != target) {
            RpgProgressionService.showTeleportFailure(
                    requester, "message.rpgcore.teleport.target_invalid");
            return;
        }

        PlayerRpgData targetData = target.getData(ModAttachments.PLAYER_RPG_DATA.get());
        if (!targetData.teleportUnlocked()) {
            RpgProgressionService.showTeleportFailure(
                    requester, "message.rpgcore.teleport.request_send_failed");
            return;
        }

        expireRequests(server, System.currentTimeMillis());

        Map<UUID, PendingRequest> targetRequests =
                REQUESTS_BY_TARGET.computeIfAbsent(target.getUUID(), ignored -> new HashMap<>());
        if (targetRequests.containsKey(requester.getUUID())) {
            RpgProgressionService.showTeleportFailure(
                    requester, "message.rpgcore.teleport.request_already_pending");
            return;
        }

        String requesterName = requester.getGameProfile().getName();
        PendingRequest pending = new PendingRequest(
                requester.getUUID(),
                target.getUUID(),
                requesterName,
                System.currentTimeMillis() + REQUEST_TIMEOUT_MILLIS,
                ++nextRequestSequence);
        targetRequests.put(requester.getUUID(), pending);

        target.sendSystemMessage(Component.translatable(
                "message.rpgcore.teleport.request_received",
                requesterName, requesterName, requesterName));
        requester.displayClientMessage(Component.translatable(
                "message.rpgcore.teleport.request_sent",
                target.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW), true);
    }

    /** Accepts the newest outstanding request. */
    private static int acceptLatest(ServerPlayer target) {
        MinecraftServer server = target.getServer();
        if (server == null) {
            showNoPendingRequests(target);
            return 0;
        }
        expireRequests(server, System.currentTimeMillis());

        List<PendingRequest> pending = pendingForTarget(target.getUUID());
        if (pending.isEmpty()) {
            showNoPendingRequests(target);
            return 0;
        }
        return acceptPending(target, pending.get(0).requesterId(), server);
    }

    /** Accepts the request from the named player. */
    private static int accept(ServerPlayer target, String requesterName) {
        MinecraftServer server = target.getServer();
        if (server == null) {
            RpgProgressionService.showTeleportFailure(
                    target, "message.rpgcore.teleport.no_pending_request", requesterName);
            return 0;
        }
        expireRequests(server, System.currentTimeMillis());

        PendingRequest pending = findPendingByName(target.getUUID(), requesterName);
        if (pending == null) {
            RpgProgressionService.showTeleportFailure(
                    target, "message.rpgcore.teleport.no_pending_request", requesterName);
            return 0;
        }
        return acceptPending(target, pending.requesterId(), server);
    }

    /** Accepts every currently outstanding request addressed to this player. */
    private static int acceptAll(ServerPlayer target) {
        MinecraftServer server = target.getServer();
        if (server == null) {
            showNoPendingRequests(target);
            return 0;
        }
        expireRequests(server, System.currentTimeMillis());

        List<PendingRequest> pending = pendingForTarget(target.getUUID());
        if (pending.isEmpty()) {
            showNoPendingRequests(target);
            return 0;
        }

        int accepted = 0;
        for (PendingRequest request : pending) {
            int result = acceptPending(target, request.requesterId(), server);
            if (result == Command.SINGLE_SUCCESS) {
                accepted++;
            }
        }

        target.sendSystemMessage(Component.translatable(
                "message.rpgcore.teleport.accept_all_summary", accepted));
        return accepted > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    /** Removes and processes one request by requester UUID. */
    private static int acceptPending(
            ServerPlayer target, UUID requesterId, MinecraftServer server) {
        PendingRequest pending = removePending(target.getUUID(), requesterId);
        if (pending == null) {
            showNoPendingRequests(target);
            return 0;
        }

        ServerPlayer requester = server.getPlayerList().getPlayer(pending.requesterId());
        if (requester == null || requester.isRemoved()) {
            RpgProgressionService.showTeleportFailure(
                    target, "message.rpgcore.teleport.requester_offline", pending.requesterName());
            return 0;
        }

        PlayerRpgData targetData = target.getData(ModAttachments.PLAYER_RPG_DATA.get());
        if (!targetData.teleportUnlocked()) {
            RpgProgressionService.showTeleportFailure(
                    requester, "message.rpgcore.teleport.target_no_longer_eligible",
                    target.getGameProfile().getName());
            RpgProgressionService.showTeleportFailure(
                    target, "message.rpgcore.teleport.target_no_longer_eligible_self");
            return 0;
        }

        if (!RpgProgressionService.completeTeleport(requester, target)) {
            return 0;
        }

        target.sendSystemMessage(Component.translatable(
                "message.rpgcore.teleport.request_accepted_target",
                requester.getGameProfile().getName()).withStyle(ChatFormatting.GREEN));
        cancelOtherRequestsFrom(requester, target.getUUID(), server);
        return Command.SINGLE_SUCCESS;
    }

    /** Denies the newest outstanding request. */
    private static int denyLatest(ServerPlayer target) {
        MinecraftServer server = target.getServer();
        if (server == null) {
            showNoPendingRequests(target);
            return 0;
        }
        expireRequests(server, System.currentTimeMillis());

        List<PendingRequest> pending = pendingForTarget(target.getUUID());
        if (pending.isEmpty()) {
            showNoPendingRequests(target);
            return 0;
        }
        return denyPending(target, pending.get(0).requesterId(), server);
    }

    /** Denies the request from the named player. */
    private static int deny(ServerPlayer target, String requesterName) {
        MinecraftServer server = target.getServer();
        if (server == null) {
            RpgProgressionService.showTeleportFailure(
                    target, "message.rpgcore.teleport.no_pending_request", requesterName);
            return 0;
        }
        expireRequests(server, System.currentTimeMillis());

        PendingRequest pending = findPendingByName(target.getUUID(), requesterName);
        if (pending == null) {
            RpgProgressionService.showTeleportFailure(
                    target, "message.rpgcore.teleport.no_pending_request", requesterName);
            return 0;
        }
        return denyPending(target, pending.requesterId(), server);
    }

    /** Denies all currently outstanding requests addressed to this player. */
    private static int denyAll(ServerPlayer target) {
        MinecraftServer server = target.getServer();
        if (server == null) {
            showNoPendingRequests(target);
            return 0;
        }
        expireRequests(server, System.currentTimeMillis());

        List<PendingRequest> pending = pendingForTarget(target.getUUID());
        if (pending.isEmpty()) {
            showNoPendingRequests(target);
            return 0;
        }

        int denied = 0;
        for (PendingRequest request : pending) {
            if (denyPending(target, request.requesterId(), server) == Command.SINGLE_SUCCESS) {
                denied++;
            }
        }

        target.sendSystemMessage(Component.translatable(
                "message.rpgcore.teleport.deny_all_summary", denied));
        return denied > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    /** Removes and denies one request by requester UUID. */
    private static int denyPending(
            ServerPlayer target, UUID requesterId, MinecraftServer server) {
        PendingRequest pending = removePending(target.getUUID(), requesterId);
        if (pending == null) {
            return 0;
        }

        ServerPlayer requester = server.getPlayerList().getPlayer(pending.requesterId());
        if (requester == null || requester.isRemoved()) {
            RpgProgressionService.showTeleportFailure(
                    target, "message.rpgcore.teleport.requester_offline", pending.requesterName());
            return 0;
        }

        RpgProgressionService.showTeleportFailure(
                requester, "message.rpgcore.teleport.request_denied",
                target.getGameProfile().getName());
        target.sendSystemMessage(Component.translatable(
                "message.rpgcore.teleport.request_denied_target",
                pending.requesterName()).withStyle(ChatFormatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static void showNoPendingRequests(ServerPlayer target) {
        RpgProgressionService.showTeleportFailure(
                target, "message.rpgcore.teleport.no_pending_requests");
    }

    /** Returns pending requests newest-first. */
    private static List<PendingRequest> pendingForTarget(UUID targetId) {
        Map<UUID, PendingRequest> requests = REQUESTS_BY_TARGET.get(targetId);
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        return requests.values().stream()
                .sorted(Comparator.comparingLong(PendingRequest::sequence).reversed())
                .toList();
    }

    private static PendingRequest findPendingByName(UUID targetId, String requesterName) {
        Map<UUID, PendingRequest> requests = REQUESTS_BY_TARGET.get(targetId);
        if (requests == null) {
            return null;
        }
        for (PendingRequest pending : requests.values()) {
            if (pending.requesterName().equalsIgnoreCase(requesterName)) {
                return pending;
            }
        }
        return null;
    }

    /** Removes one matching request by target and requester UUID. */
    private static PendingRequest removePending(UUID targetId, UUID requesterId) {
        Map<UUID, PendingRequest> targetRequests = REQUESTS_BY_TARGET.get(targetId);
        if (targetRequests == null) {
            return null;
        }
        PendingRequest removed = targetRequests.remove(requesterId);
        if (targetRequests.isEmpty()) {
            REQUESTS_BY_TARGET.remove(targetId);
        }
        return removed;
    }

    /** Expire requests and notify both online players. */
    public static void onServerTick(ServerTickEvent.Post event) {
        expireRequests(event.getServer(), System.currentTimeMillis());
    }

    private static void expireRequests(MinecraftServer server, long now) {
        Iterator<Map.Entry<UUID, Map<UUID, PendingRequest>>> targetIterator =
                REQUESTS_BY_TARGET.entrySet().iterator();
        while (targetIterator.hasNext()) {
            Map.Entry<UUID, Map<UUID, PendingRequest>> targetEntry = targetIterator.next();
            Iterator<Map.Entry<UUID, PendingRequest>> requestIterator =
                    targetEntry.getValue().entrySet().iterator();

            while (requestIterator.hasNext()) {
                PendingRequest pending = requestIterator.next().getValue();
                if (now < pending.expiresAtMillis()) {
                    continue;
                }

                requestIterator.remove();
                ServerPlayer requester = server.getPlayerList().getPlayer(pending.requesterId());
                if (requester != null) {
                    RpgProgressionService.showTeleportFailure(
                            requester, "message.rpgcore.teleport.request_expired");
                }

                ServerPlayer target = server.getPlayerList().getPlayer(pending.targetId());
                if (target != null) {
                    target.sendSystemMessage(Component.translatable(
                            "message.rpgcore.teleport.request_expired_target",
                            pending.requesterName()));
                }
            }

            if (targetEntry.getValue().isEmpty()) {
                targetIterator.remove();
            }
        }
    }

    /** Remove requests on logout so stale entries cannot be accepted later. */
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer leavingPlayer)) {
            return;
        }
        MinecraftServer server = leavingPlayer.getServer();
        if (server == null) {
            return;
        }

        UUID leavingId = leavingPlayer.getUUID();

        Map<UUID, PendingRequest> requestsSentToLeavingPlayer =
                REQUESTS_BY_TARGET.remove(leavingId);
        if (requestsSentToLeavingPlayer != null) {
            for (PendingRequest pending : requestsSentToLeavingPlayer.values()) {
                ServerPlayer requester =
                        server.getPlayerList().getPlayer(pending.requesterId());
                if (requester != null && requester != leavingPlayer) {
                    RpgProgressionService.showTeleportFailure(
                            requester, "message.rpgcore.teleport.target_went_offline",
                            leavingPlayer.getGameProfile().getName());
                }
            }
        }

        Iterator<Map.Entry<UUID, Map<UUID, PendingRequest>>> targetIterator =
                REQUESTS_BY_TARGET.entrySet().iterator();
        while (targetIterator.hasNext()) {
            Map.Entry<UUID, Map<UUID, PendingRequest>> targetEntry = targetIterator.next();
            PendingRequest cancelled = targetEntry.getValue().remove(leavingId);
            if (cancelled != null) {
                ServerPlayer target = server.getPlayerList().getPlayer(targetEntry.getKey());
                if (target != null && target != leavingPlayer) {
                    target.sendSystemMessage(Component.translatable(
                            "message.rpgcore.teleport.requester_went_offline",
                            cancelled.requesterName()));
                }
            }
            if (targetEntry.getValue().isEmpty()) {
                targetIterator.remove();
            }
        }
    }

    /** Cancel this requester's other outstanding requests after one teleport succeeds. */
    private static void cancelOtherRequestsFrom(
            ServerPlayer requester, UUID acceptedTargetId, MinecraftServer server) {
        Iterator<Map.Entry<UUID, Map<UUID, PendingRequest>>> targetIterator =
                REQUESTS_BY_TARGET.entrySet().iterator();
        while (targetIterator.hasNext()) {
            Map.Entry<UUID, Map<UUID, PendingRequest>> targetEntry = targetIterator.next();
            if (targetEntry.getKey().equals(acceptedTargetId)) {
                continue;
            }

            PendingRequest cancelled = targetEntry.getValue().remove(requester.getUUID());
            if (cancelled != null) {
                ServerPlayer otherTarget =
                        server.getPlayerList().getPlayer(targetEntry.getKey());
                if (otherTarget != null) {
                    otherTarget.sendSystemMessage(Component.translatable(
                            "message.rpgcore.teleport.requester_cancelled_other",
                            cancelled.requesterName()));
                }
            }
            if (targetEntry.getValue().isEmpty()) {
                targetIterator.remove();
            }
        }
    }
}
