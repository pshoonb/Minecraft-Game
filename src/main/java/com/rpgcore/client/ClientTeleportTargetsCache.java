package com.rpgcore.client;

import com.rpgcore.network.TeleportTargetsPayload;
import java.util.List;

public final class ClientTeleportTargetsCache {
    private static List<TeleportTargetsPayload.Target> targets = List.of();
    private ClientTeleportTargetsCache() { }

    public static void update(List<TeleportTargetsPayload.Target> next) {
        targets = List.copyOf(next);
    }

    public static List<TeleportTargetsPayload.Target> get() { return targets; }
    public static void clear() { targets = List.of(); }
}
