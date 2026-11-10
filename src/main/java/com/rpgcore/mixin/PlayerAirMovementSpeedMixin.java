package com.rpgcore.mixin;

import com.rpgcore.attributes.AttributeFormulaCalculator;
import com.rpgcore.data.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the Speed attribute to the actual player airborne steering value.
 *
 * <p>Player overrides LivingEntity#getFlyingSpeed, so the mixin must target
 * Player rather than LivingEntity. The vanilla return value already accounts
 * for sprinting; only the RPG percentage is applied here.</p>
 */
@Mixin(value = Player.class, remap = false)
public abstract class PlayerAirMovementSpeedMixin {
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

        // Do not add another sprint multiplier: Player#getFlyingSpeed already
        // returns the sprint-adjusted vanilla air-control value.
        double adjustedSpeed = callback.getReturnValue() * (1.0 + bonusPercent / 100.0);
        if (Double.isFinite(adjustedSpeed) && adjustedSpeed > 0.0) {
            callback.setReturnValue((float) Math.min(adjustedSpeed, Float.MAX_VALUE));
        }
    }
}
