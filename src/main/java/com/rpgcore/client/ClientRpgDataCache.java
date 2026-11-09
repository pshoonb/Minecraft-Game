package com.rpgcore.client;

import com.rpgcore.data.PlayerRpgData;

public final class ClientRpgDataCache {
    private static PlayerRpgData current = PlayerRpgData.EMPTY;
    private static boolean receivedServerData;

    private ClientRpgDataCache() {
    }

    public static void update(PlayerRpgData data) {
        current = data;
        receivedServerData = true;
    }

    public static PlayerRpgData get() {
        return current;
    }

    public static boolean hasServerData() {
        return receivedServerData;
    }

    public static void clear() {
        current = PlayerRpgData.EMPTY;
        receivedServerData = false;
    }
}
