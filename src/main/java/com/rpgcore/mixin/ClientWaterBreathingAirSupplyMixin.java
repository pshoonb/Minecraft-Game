package com.rpgcore.mixin;

import com.rpgcore.attributes.AttributeFormulaCalculator;
import com.rpgcore.client.ClientRpgDataCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps the local player's oxygen HUD in sync with the server-calculated capacity. */
@Mixin(value = Entity.class, remap = false)
public abstract class ClientWaterBreathingAirSupplyMixin {
    @Inject(method = "getMaxAirSupply", at = @At("RETURN"), cancellable = true, remap = false)
    private void rpgcore$applyClientWaterBreathingAir(CallbackInfoReturnable<Integer> callback) {
        if (!((Object) this instanceof LocalPlayer player)
                || Minecraft.getInstance().player != player
                || !ClientRpgDataCache.hasServerData()) {
            return;
        }

        int bonusTicks = AttributeFormulaCalculator.waterBreathingBonusAirTicks(
                ClientRpgDataCache.get().waterBreathing());
        if (bonusTicks <= 0) {
            return;
        }

        long updatedMaximum = (long) callback.getReturnValue() + bonusTicks;
        callback.setReturnValue((int) Math.min(Integer.MAX_VALUE, updatedMaximum));
    }
}