package com.rpgcore;

import com.rpgcore.combat.CombatParticipationTracker;
import com.rpgcore.data.ModAttachments;
import com.rpgcore.network.NetworkHandler;
import com.rpgcore.network.PlayerSyncEvents;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@Mod(RpgCoreMod.MOD_ID)
public final class RpgCoreMod {
    public static final String MOD_ID = "rpgcore";

    public RpgCoreMod(IEventBus modEventBus) {
        ModAttachments.register(modEventBus);
        modEventBus.addListener(NetworkHandler::registerPayloads);

        NeoForge.EVENT_BUS.addListener(PlayerSyncEvents::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(PlayerSyncEvents::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(CombatParticipationTracker::onLivingDamage);
        NeoForge.EVENT_BUS.addListener(CombatParticipationTracker::onPlayerLoggedOut);

        // Take the snapshot after other death-event listeners have had a chance
        // to cancel the death, and process the final XP amount after other mods.
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST, CombatParticipationTracker::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST, CombatParticipationTracker::onExperienceDrop);
        NeoForge.EVENT_BUS.addListener(CombatParticipationTracker::onEntityLeaveLevel);
        NeoForge.EVENT_BUS.addListener(CombatParticipationTracker::onServerTick);
    }
}
