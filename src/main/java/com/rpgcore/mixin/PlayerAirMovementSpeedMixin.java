package com.rpgcore.mixin;

import com.rpgcore.attributes.AttributeFormulaCalculator;
import com.rpgcore.data.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the player's Speed attribute at the vanilla airborne acceleration
 * calculation point, using the current movement state rather than tick-pre
 * input fields from the preceding tick.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class PlayerAirMovementSpeedMixin {
    private static final double SPRINT_MOVEMENT_MULTIPLIER = 1.3;

    @Inject(
            method = "getFlyingSpeed",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void rpgcore$applyAirMovementSpeed(CallbackInfoReturnable<Float> callback) {
        if (!((Object) this instanceof ServerPlayer player)) {
            return;
        }

        long speedPoints = player.getData(ModAttachments.PLAYER_RPG_DATA.get()).speed();
        double bonusPercent = AttributeFormulaCalculator.movementSpeedPercent(speedPoints);
        if (!Double.isFinite(bonusPercent) || bonusPercent <= 0.0) {
            return;
        }

        double sprintMultiplier = player.isSprinting() ? SPRINT_MOVEMENT_MULTIPLIER : 1.0;
        double adjustedSpeed = callback.getReturnValue() * sprintMultiplier
                * (1.0 + bonusPercent / 100.0);
        if (Double.isFinite(adjustedSpeed) && adjustedSpeed > 0.0) {
            callback.setReturnValue((float) Math.min(adjustedSpeed, Float.MAX_VALUE));
        }
    }
}