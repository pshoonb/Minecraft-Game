package com.rpgcore.progression;

import com.rpgcore.data.PlayerRpgData;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Applies the seven allocated RPG attributes to vanilla player mechanics.
 *
 * Attribute growth is linear for the first 25 effective points, then follows
 * a logarithmic soft cap. The curve never hard-caps, so late-game investment
 * continues to improve the character.
 */
public final class RpgGameplayEffects {
    private static final double SOFT_CAP_START = 25.0D;
    private static final double SOFT_CAP_SCALE = 15.0D;

    // Tuning is designed around the practical level 100-150 range.
    // The first 25 effective points remain fully useful for early-game feedback.
    private static final double STRENGTH_DAMAGE_PER_POINT = 0.10D;
    private static final double HEALTH_PER_POINT = 0.25D;
    private static final double DEFENSE_ARMOR_PER_POINT = 0.15D;
    private static final double MINING_SPEED_PER_POINT = 0.01D;
    private static final double MINING_EFFICIENCY_PER_POINT = 0.15D;
    private static final double LUCK_PER_POINT = 0.10D;
    private static final double SPEED_PER_POINT = 0.005D;
    private static final double JUMP_VELOCITY_PER_POINT = 0.005D;

    private static final double MAX_UNIVERSAL_DAMAGE_REDUCTION = 0.40D;
    private static final double MAX_BURNING_TIME_REDUCTION = 0.60D;
    private static final double MAX_EXPLOSION_KNOCKBACK_RESISTANCE = 0.70D;

    private static final ResourceLocation STRENGTH_DAMAGE_ID = id("strength_damage");
    private static final ResourceLocation HEALTH_MAX_HEALTH_ID = id("health_max_health");
    private static final ResourceLocation DEFENSE_ARMOR_ID = id("defense_armor");
    private static final ResourceLocation DEFENSE_BURNING_TIME_ID = id("defense_burning_time");
    private static final ResourceLocation DEFENSE_EXPLOSION_KNOCKBACK_ID = id("defense_explosion_knockback");
    private static final ResourceLocation MINING_SPEED_ID = id("mining_speed");
    private static final ResourceLocation MINING_EFFICIENCY_ID = id("mining_efficiency");
    private static final ResourceLocation LUCK_ID = id("luck");
    private static final ResourceLocation SPEED_ID = id("speed");

    private RpgGameplayEffects() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("rpgcore", path);
    }

    /**
     * Applies the same uniquely identified modifiers on client and server.
     * Reapplication replaces each modifier instead of stacking it.
     */
    public static void apply(Player player, PlayerRpgData data) {
        double strength = effectiveAttributePoints(data.strength());
        double health = effectiveAttributePoints(data.health());
        double defense = effectiveAttributePoints(data.defense());
        double miningSpeed = effectiveAttributePoints(data.miningSpeed());
        double luck = effectiveAttributePoints(data.luck());
        double speed = effectiveAttributePoints(data.speed());

        replaceModifier(player, Attributes.ATTACK_DAMAGE, STRENGTH_DAMAGE_ID,
                strength * STRENGTH_DAMAGE_PER_POINT, AttributeModifier.Operation.ADD_VALUE);
        replaceModifier(player, Attributes.MAX_HEALTH, HEALTH_MAX_HEALTH_ID,
                health * HEALTH_PER_POINT, AttributeModifier.Operation.ADD_VALUE);

        // The direct armor bonus is capped, while other defense benefits
        // continue to scale after vanilla armor reaches its effective range.
        replaceModifier(player, Attributes.ARMOR, DEFENSE_ARMOR_ID,
                Math.min(20.0D, defense * DEFENSE_ARMOR_PER_POINT),
                AttributeModifier.Operation.ADD_VALUE);

        double burnReduction = MAX_BURNING_TIME_REDUCTION * defense / (defense + 30.0D);
        replaceModifier(player, Attributes.BURNING_TIME, DEFENSE_BURNING_TIME_ID,
                -burnReduction, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        double explosionKnockback = MAX_EXPLOSION_KNOCKBACK_RESISTANCE
                * defense / (defense + 40.0D);
        replaceModifier(player, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE,
                DEFENSE_EXPLOSION_KNOCKBACK_ID, explosionKnockback,
                AttributeModifier.Operation.ADD_VALUE);

        // BLOCK_BREAK_SPEED affects general breaking speed. MINING_EFFICIENCY
        // adds extra speed when the held tool is suitable for the block.
        replaceModifier(player, Attributes.BLOCK_BREAK_SPEED, MINING_SPEED_ID,
                miningSpeed * MINING_SPEED_PER_POINT,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        replaceModifier(player, Attributes.MINING_EFFICIENCY, MINING_EFFICIENCY_ID,
                miningSpeed * MINING_EFFICIENCY_PER_POINT,
                AttributeModifier.Operation.ADD_VALUE);

        replaceModifier(player, Attributes.LUCK, LUCK_ID,
                luck * LUCK_PER_POINT, AttributeModifier.Operation.ADD_VALUE);
        replaceModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID,
                speed * SPEED_PER_POINT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }

    /** Reduces final damage after vanilla armor and enchantment handling. */
    public static float damageMultiplier(long defensePoints) {
        double defense = effectiveAttributePoints(defensePoints);
        double reduction = MAX_UNIVERSAL_DAMAGE_REDUCTION * defense / (defense + 50.0D);
        return (float) (1.0D - reduction);
    }

    /**
     * Modifies upward velocity at the actual jump event.
     * Called on both local-client prediction and the logical server player.
     */
    public static void applyJumpHeight(Player player, PlayerRpgData data) {
        double jumpScale = effectiveAttributePoints(data.jumpHeight())
                * JUMP_VELOCITY_PER_POINT;
        if (jumpScale <= 0.0D) {
            return;
        }

        Vec3 velocity = player.getDeltaMovement();
        if (velocity.y > 0.0D) {
            player.setDeltaMovement(velocity.x, velocity.y * (1.0D + jumpScale), velocity.z);
        }
    }

    /**
     * First 25 points are linear. Beyond that each point has diminishing
     * returns, but the function has no hard cap.
     */
    public static double effectiveAttributePoints(long points) {
        double safePoints = Math.max(0L, points);
        if (safePoints <= SOFT_CAP_START) {
            return safePoints;
        }
        return SOFT_CAP_START + SOFT_CAP_SCALE
                * Math.log1p((safePoints - SOFT_CAP_START) / SOFT_CAP_SCALE);
    }

    private static void replaceModifier(Player player,
                                        Holder<Attribute> attribute,
                                        ResourceLocation id,
                                        double amount,
                                        AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }

        if (Math.abs(amount) < 1.0E-9D) {
            instance.removeModifier(id);
            return;
        }

        instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
    }
}
