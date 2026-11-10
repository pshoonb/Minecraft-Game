package com.rpgcore.mixin;

import com.rpgcore.attributes.AttributeFormulaCalculator;
import com.rpgcore.data.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds Water Breathing's calculated air ticks to player oxygen capacity on the server. */
@Mixin(Entity.class)
public abstract class WaterBreathingAirSupplyMixin {
    @Inject(method = "getMaxAirSupply", at = @At("RETURN"), cancellable = true)
    private void rpgcore$applyWaterBreathingAir(CallbackInfoReturnable<Integer> callback) {
        if (!((Object) this instanceof ServerPlayer player)) {
            return;
        }

        long points = player.getData(ModAttachments.PLAYER_RPG_DATA.get()).waterBreathing();
        int bonusTicks = AttributeFormulaCalculator.waterBreathingBonusAirTicks(points);
        if (bonusTicks <= 0) {
            return;
        }

        long updatedMaximum = (long) callback.getReturnValue() + bonusTicks;
        callback.setReturnValue((int) Math.min(Integer.MAX_VALUE, updatedMaximum));
    }
}