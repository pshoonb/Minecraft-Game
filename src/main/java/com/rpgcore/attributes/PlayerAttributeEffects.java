package com.rpgcore.attributes;

import com.rpgcore.RpgCoreMod;
import com.rpgcore.data.PlayerRpgData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Applies persistent RPG data to the player's vanilla attribute instances. */
public final class PlayerAttributeEffects {
    private static final ResourceLocation HEALTH_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(RpgCoreMod.MOD_ID, "rpg_health");
    private static final ResourceLocation MOVEMENT_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(RpgCoreMod.MOD_ID, "rpg_movement_speed");

    private PlayerAttributeEffects() {
    }

    public static void apply(ServerPlayer player, PlayerRpgData data) {
        applyHealth(player, data);
        applyMovementSpeed(player, data);
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
        AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }

        attribute.removeModifier(MOVEMENT_MODIFIER_ID);
        double bonusPercent = AttributeFormulaCalculator.movementSpeedPercent(data.speed());
        if (bonusPercent > 0.0) {
            attribute.addOrUpdateTransientModifier(new AttributeModifier(
                    MOVEMENT_MODIFIER_ID,
                    bonusPercent / 100.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }
}
