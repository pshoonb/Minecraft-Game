package com.rpgcore.mixin;

import com.rpgcore.attributes.AttributeFormulaCalculator;
import com.rpgcore.client.ClientRpgDataCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the same Speed percentage to the local player's predicted air steering. */
@Mixin(value = Player.class, remap = false)
public abstract class ClientPlayerAirMovementSpeedMixin {
    @Inject(
            method = "getFlyingSpeed",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void rpgcore$applyClientAirMovementSpeed(CallbackInfoReturnable<Float> callback) {
        if (!((Object) this instanceof LocalPlayer player)
                || Minecraft.getInstance().player != player
                || !ClientRpgDataCache.hasServerData()) {
            return;
        }

        long speedPoints = ClientRpgDataCache.get().speed();
        double bonusPercent = AttributeFormulaCalculator.movementSpeedPercent(speedPoints);
        if (!Double.isFinite(bonusPercent) || bonusPercent <= 0.0) {
            return;
        }

        // Vanilla already changes the base air-control value while sprinting.
        // Applying the same RPG percentage as on the server keeps prediction aligned.
        double adjustedSpeed = callback.getReturnValue() * (1.0 + bonusPercent / 100.0);
        if (Double.isFinite(adjustedSpeed) && adjustedSpeed > 0.0) {
            callback.setReturnValue((float) Math.min(adjustedSpeed, Float.MAX_VALUE));
        }
    }
}
