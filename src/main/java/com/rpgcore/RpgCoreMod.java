package com.rpgcore;

import com.rpgcore.config.RpgCoreConfig;
import com.rpgcore.data.ModAttachments;
import com.rpgcore.network.NetworkHandler;
import com.rpgcore.network.PlayerSyncEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;

@Mod(RpgCoreMod.MOD_ID)
public final class RpgCoreMod {
    public static final String MOD_ID = "rpgcore";

    public RpgCoreMod(IEventBus modEventBus) {
        ModAttachments.register(modEventBus);
        modEventBus.addListener(NetworkHandler::registerPayloads);
        ModLoadingContext.get().registerConfig(ModConfigSpec.Type.COMMON, RpgCoreConfig.SPEC);

        NeoForge.EVENT_BUS.addListener(PlayerSyncEvents::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(PlayerSyncEvents::onPlayerRespawn);
    }
}
