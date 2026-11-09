package com.rpgcore.progression;

import com.rpgcore.data.PlayerRpgData;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Applies the seven allocated RPG attributes to vanilla player mechanics.
 *
 * <p>These are first-pass prototype tuning values. Each is centralized here
 * so balance can be adjusted without changing saved data, networking, or UI.</p>
 *
 * <p>Attribute points use square-root scaling and a 10,000-point input cap to
 * keep the agreed progression-point curve from producing unbounded bonuses.</p>
 */
public final class RpgGameplayEffects {
    private static final long ATTRIBUTE_POINTS_SOFT_CAP = 10_000L;

    private static final double STRENGTH_DAMAGE_PER_ROOT_POINT = 0.10D;
    private static final double HEALTH_PER_ROOT_POINT = 0.50D;
    private static final double DEFENSE_ARMOR_PER_ROOT_POINT = 0.20D;
    private static final double MINING_SPEED_PER_ROOT_POINT = 0.005D;
    private static final double LUCK_PER_ROOT_POINT = 0.05D;
    private static final double SPEED_PER_ROOT_POINT = 0.0015D;
    private static final double JUMP_VELOCITY_PER_ROOT_POINT = 0.002D;

    private static final ResourceLocation STRENGTH_DAMAGE_ID =
            id("strength_damage");
    private static final ResourceLocation HEALTH_MAX_HEALTH_ID =
            id("health_max_health");
    private static final ResourceLocation DEFENSE_ARMOR_ID =
            id("defense_armor");
    private static final ResourceLocation MINING_SPEED_ID =
            id("mining_speed");
    private static final ResourceLocation LUCK_ID =
            id("luck");
    private static final ResourceLocation SPEED_ID =
            id("speed");

    private RpgGameplayEffects() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("rpgcore", path);
    }

    /**
     * Replaces this mod's transient modifiers using current server-side data.
     * Reapplying is safe on allocation, reset, login, and respawn.
     */
    public static void apply(ServerPlayer player, PlayerRpgData data) {
        double strength = effectiveAttributePoints(data.strength());
        double health = effectiveAttributePoints(data.health());
        double defense = effectiveAttributePoints(data.defense());
        double miningSpeed = effectiveAttributePoints(data.miningSpeed());
        double luck = effectiveAttributePoints(data.luck());
        double speed = effectiveAttributePoints(data.speed());

        replaceModifier(player, Attributes.ATTACK_DAMAGE, STRENGTH_DAMAGE_ID,
                strength * STRENGTH_DAMAGE_PER_ROOT_POINT, AttributeModifier.Operation.ADD_VALUE);
        replaceModifier(player, Attributes.MAX_HEALTH, HEALTH_MAX_HEALTH_ID,
                health * HEALTH_PER_ROOT_POINT, AttributeModifier.Operation.ADD_VALUE);
        replaceModifier(player, Attributes.ARMOR, DEFENSE_ARMOR_ID,
                defense * DEFENSE_ARMOR_PER_ROOT_POINT, AttributeModifier.Operation.ADD_VALUE);
        replaceModifier(player, Attributes.BLOCK_BREAK_SPEED, MINING_SPEED_ID,
                miningSpeed * MINING_SPEED_PER_ROOT_POINT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        replaceModifier(player, Attributes.LUCK, LUCK_ID,
                luck * LUCK_PER_ROOT_POINT, AttributeModifier.Operation.ADD_VALUE);
        replaceModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID,
                speed * SPEED_PER_ROOT_POINT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        // A lower maximum health after a reset must not leave current health above the cap.
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }

    /** Adds the jump-height bonus to the upward velocity of an actual player jump. */
    public static void applyJumpHeight(ServerPlayer player, PlayerRpgData data) {
        double jumpScale = effectiveAttributePoints(data.jumpHeight())
                * JUMP_VELOCITY_PER_ROOT_POINT;
        if (jumpScale <= 0.0D) {
            return;
        }

        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(velocity.x, velocity.y * (1.0D + jumpScale), velocity.z);
    }

    private static double effectiveAttributePoints(long points) {
        long safePoints = Math.max(0L, Math.min(points, ATTRIBUTE_POINTS_SOFT_CAP));
        return Math.sqrt(safePoints);
    }

    private static void replaceModifier(ServerPlayer player,
                                        Holder<Attribute> attribute,
                                        ResourceLocation id,
                                        double amount,
                                        AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }

        instance.removeModifier(id);
        if (amount > 0.0D) {
            instance.addTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }
}
