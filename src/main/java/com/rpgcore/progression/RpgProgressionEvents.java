package com.rpgcore.progression;

import com.rpgcore.RpgCoreMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/** Mirrors vanilla experience-level changes into the RPG attribute snapshot. */
@EventBusSubscriber(modid = RpgCoreMod.MOD_ID)
public final class RpgProgressionEvents {
    private RpgProgressionEvents() {}

    @SubscribeEvent
    public static void onVanillaLevelChange(PlayerXpEvent.LevelChange event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            int resultingLevel = player.experienceLevel + event.getLevels();
            RpgProgressionService.syncVanillaLevel(player, resultingLevel);
        }
    }
}
