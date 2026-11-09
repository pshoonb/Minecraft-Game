package com.rpgcore.progression;

import com.rpgcore.RpgCoreMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/**
 * Vanilla XP pickup also grants RPG XP. The vanilla orb is not cancelled or
 * reduced, so enchanting and the vanilla XP bar remain unchanged.
 */
@EventBusSubscriber(modid = RpgCoreMod.MOD_ID)
public final class RpgProgressionEvents {
    private RpgProgressionEvents() {}

    @SubscribeEvent
    public static void onExperienceOrbPickup(PlayerXpEvent.PickupXp event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            int value = event.getOrb().getValue();
            if (value > 0) RpgProgressionService.addExperience(player, value);
        }
    }
}
