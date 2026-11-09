package com.rpgcore;

import com.rpgcore.data.ModAttachments;
import com.rpgcore.network.NetworkHandler;
import com.rpgcore.network.PlayerSyncEvents;
import com.rpgcore.progression.RpgProgressionEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(RpgCoreMod.MOD_ID)
public final class RpgCoreMod {
    public static final String MOD_ID = "rpgcore";

    public RpgCoreMod(IEventBus modEventBus) {
        ModAttachments.register(modEventBus);
        modEventBus.addListener(NetworkHandler::registerPayloads);

        NeoForge.EVENT_BUS.addListener(PlayerSyncEvents::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(PlayerSyncEvents::onPlayerRespawn);

        // Register the gameplay hooks explicitly so their server-side execution
        // does not depend on automatic event-subscriber discovery.
        NeoForge.EVENT_BUS.addListener(RpgProgressionEvents::onVanillaLevelChange);
        NeoForge.EVENT_BUS.addListener(RpgProgressionEvents::onPlayerJump);
        NeoForge.EVENT_BUS.addListener(RpgProgressionEvents::onPlayerTick);
    }
}
