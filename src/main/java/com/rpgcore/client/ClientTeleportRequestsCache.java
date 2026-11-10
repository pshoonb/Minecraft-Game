package com.rpgcore.client;

import com.rpgcore.network.TeleportIncomingRequestsPayload;

import java.util.List;

/** Client-side snapshot of requests waiting for the local player. */
public final class ClientTeleportRequestsCache {
    private static List<TeleportIncomingRequestsPayload.Request> requests = List.of();

    private ClientTeleportRequestsCache() {
    }

    public static void update(List<TeleportIncomingRequestsPayload.Request> next) {
        requests = List.copyOf(next);
    }

    public static List<TeleportIncomingRequestsPayload.Request> get() {
        return requests;
    }

    public static void clear() {
        requests = List.of();
    }
}
