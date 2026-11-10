package com.rpgcore.attributes;

import com.rpgcore.RpgCoreMod;
import com.rpgcore.data.PlayerRpgData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForgeMod;

/** Applies persistent RPG data to the player's vanilla attribute instances. */
public final class PlayerAttributeEffects {
    private static final ResourceLocation HEALTH_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(RpgCoreMod.MOD_ID, "rpg_health");
    private static final ResourceLocation MOVEMENT_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(RpgCoreMod.MOD_ID, "rpg_movement_speed");
    private static final ResourceLocation SWIM_SPEED_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(RpgCoreMod.MOD_ID, "rpg_swim_speed");
    private static final ResourceLocation SAFE_FALL_DISTANCE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(RpgCoreMod.MOD_ID, "rpg_safe_fall_distance");

    private PlayerAttributeEffects() {
    }

    public static void apply(ServerPlayer player, PlayerRpgData data) {
        apply(player, data, null);
    }

    /**
     * Applies the new attribute snapshot and keeps current air consistent with
     * the changed oxygen capacity. A player who was full before an allocation
     * receives the new capacity immediately; a refund clamps excess air.
     */
    public static void apply(ServerPlayer player, PlayerRpgData data, PlayerRpgData previousData) {
        applyHealth(player, data);
        applyMovementSpeed(player, data);
        applySwimSpeed(player, data);
        applySafeFallDistance(player, data);
        applyWaterBreathingAirCapacity(player, data, previousData);
    }

    private static void applyWaterBreathingAirCapacity(
            ServerPlayer player, PlayerRpgData data, PlayerRpgData previousData) {
        int newBonus = AttributeFormulaCalculator.waterBreathingBonusAirTicks(data.waterBreathing());
        int newMaximum = player.getMaxAirSupply();
        int currentAir = player.getAirSupply();

        if (previousData == null) {
            if (currentAir > newMaximum) {
                player.setAirSupply(newMaximum);
            }
            return;
        }

        // Derive the unchanged base capacity from the new mixed-in maximum so
        // other mods that customize max air remain compatible.
        int baseMaximum = Math.max(0, newMaximum - newBonus);
        int previousBonus = AttributeFormulaCalculator.waterBreathingBonusAirTicks(
                previousData.waterBreathing());
        long previousMaximum = (long) baseMaximum + previousBonus;

        if (currentAir >= previousMaximum) {
            player.setAirSupply(newMaximum);
        } else if (currentAir > newMaximum) {
            player.setAirSupply(newMaximum);
        }
    }

    private static void applyHealth(ServerPlayer player, PlayerRpgData data) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }

        float oldMaximum = player.getMaxHealth();
        attribute.removeModifier(HEALTH_MODIFIER_ID);

        double bonus = AttributeFormulaCalculator.healthBonus(data.health());
        if (bonus > 0.0) {
            attribute.addOrUpdateTransientModifier(new AttributeModifier(
                    HEALTH_MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
        }

        float newMaximum = player.getMaxHealth();
        if (newMaximum > oldMaximum) {
            // The user specified that a maximum-health increase also heals by
            // exactly the amount of the maximum-health increase, not to full.
            player.setHealth(Math.min(newMaximum, player.getHealth() + newMaximum - oldMaximum));
        } else if (player.getHealth() > newMaximum) {
            player.setHealth(newMaximum);
        }
    }

    private static void applyMovementSpeed(ServerPlayer player, PlayerRpgData data) {
        applyPercentageModifier(
                player.getAttribute(Attributes.MOVEMENT_SPEED),
                MOVEMENT_MODIFIER_ID,
                AttributeFormulaCalculator.movementSpeedPercent(data.speed()));
    }

    /**
     * Water travel uses NeoForge's separate SWIM_SPEED attribute rather than
     * the player's normal MOVEMENT_SPEED attribute. Apply the same percentage
     * so allocated Speed points also improve swimming.
     */
    private static void applySwimSpeed(ServerPlayer player, PlayerRpgData data) {
        applyPercentageModifier(
                player.getAttribute(NeoForgeMod.SWIM_SPEED),
                SWIM_SPEED_MODIFIER_ID,
                AttributeFormulaCalculator.movementSpeedPercent(data.speed()));
    }

    private static void applyPercentageModifier(
            AttributeInstance attribute, ResourceLocation modifierId, double bonusPercent) {
        if (attribute == null) {
            return;
        }

        attribute.removeModifier(modifierId);
        if (bonusPercent > 0.0) {
            attribute.addOrUpdateTransientModifier(new AttributeModifier(
                    modifierId,
                    bonusPercent / 100.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    /**
     * Jump Height increases the player's safe fall distance by exactly the
     * same number of blocks as the added jump height. Both effects use the
     * same cumulative formula, keeping upward and downward capability in sync.
     */
    private static void applySafeFallDistance(ServerPlayer player, PlayerRpgData data) {
        AttributeInstance attribute = player.getAttribute(Attributes.SAFE_FALL_DISTANCE);
        if (attribute == null) {
            return;
        }

        attribute.removeModifier(SAFE_FALL_DISTANCE_MODIFIER_ID);
        double bonusBlocks = AttributeFormulaCalculator.jumpHeightBlocks(data.jumpHeight());
        if (bonusBlocks > 0.0) {
            attribute.addOrUpdateTransientModifier(new AttributeModifier(
                    SAFE_FALL_DISTANCE_MODIFIER_ID,
                    bonusBlocks,
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
