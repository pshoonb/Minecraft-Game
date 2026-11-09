package com.rpgcore.progression;

import com.rpgcore.data.PlayerRpgData;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Applies the seven allocated RPG attributes to vanilla player mechanics.
 * First-pass prototype rates are centralized here for easy balancing.
 */
public final class RpgGameplayEffects {
    private static final long ATTRIBUTE_POINTS_SOFT_CAP = 10_000L;

    // Each value is applied per square-root of allocated attribute points.
    // These revised values are intentionally easier to notice at low allocations.
    private static final double STRENGTH_DAMAGE_PER_ROOT_POINT = 0.75D;
    private static final double HEALTH_PER_ROOT_POINT = 1.50D;
    private static final double DEFENSE_ARMOR_PER_ROOT_POINT = 0.75D;
    private static final double MINING_SPEED_PER_ROOT_POINT = 0.02D;
    private static final double LUCK_PER_ROOT_POINT = 0.15D;
    private static final double SPEED_PER_ROOT_POINT = 0.01D;
    private static final double JUMP_VELOCITY_PER_ROOT_POINT = 0.02D;

    private static final ResourceLocation STRENGTH_DAMAGE_ID = id("strength_damage");
    private static final ResourceLocation HEALTH_MAX_HEALTH_ID = id("health_max_health");
    private static final ResourceLocation DEFENSE_ARMOR_ID = id("defense_armor");
    private static final ResourceLocation MINING_SPEED_ID = id("mining_speed");
    private static final ResourceLocation LUCK_ID = id("luck");
    private static final ResourceLocation SPEED_ID = id("speed");

    private RpgGameplayEffects() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("rpgcore", path);
    }

    /**
     * Updates the player's vanilla attributes with modifiers derived from
     * server-authoritative RPG data. Existing modifiers are replaced in place.
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

        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }

    /**
     * Adds the jump-height bonus to the upward velocity of an actual player jump.
     * Called on both the local client (prediction) and the logical server.
     */
    public static void applyJumpHeight(Player player, PlayerRpgData data) {
        double jumpScale = effectiveAttributePoints(data.jumpHeight())
                * JUMP_VELOCITY_PER_ROOT_POINT;
        if (jumpScale <= 0.0D) {
            return;
        }

        Vec3 velocity = player.getDeltaMovement();
        if (velocity.y > 0.0D) {
            player.setDeltaMovement(velocity.x, velocity.y * (1.0D + jumpScale), velocity.z);
        }
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

        if (amount <= 0.0D) {
            instance.removeModifier(id);
            return;
        }

        // NeoForge/Minecraft 1.21.1 provides an explicit replace-or-add API.
        instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
    }
}
