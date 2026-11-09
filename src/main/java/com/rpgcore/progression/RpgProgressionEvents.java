package com.rpgcore.progression;

import com.rpgcore.RpgCoreMod;
import com.rpgcore.data.ModAttachments;
import com.rpgcore.data.PlayerRpgData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/** Mirrors vanilla levels and applies player-only jump-height effects. */
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

    @SubscribeEvent
    public static void onPlayerJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerRpgData data = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
            RpgGameplayEffects.applyJumpHeight(player, data);
        }
    }
}
