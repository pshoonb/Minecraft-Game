package com.rpgcore.progression;

import com.rpgcore.data.PlayerRpgData;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Applies the first playable pass of RPG progression to vanilla player attributes.
 *
 * <p>All values below are prototype tuning values. Keeping them here makes later
 * balance changes independent from persistence, networking, and the UI.</p>
 *
 * <p>Square-root scaling is intentional: the agreed attribute-point formula
 * grants increasingly large totals, so a linear conversion would grow too fast.</p>
 */
public final class RpgGameplayEffects {
    private static final double MAX_EFFECTIVE_LEVEL = 100.0D;
    private static final long ATTRIBUTE_POINTS_SOFT_CAP = 10_000L;
    private static final double MAX_EFFECTIVE_ATTRIBUTE_POINTS = 100.0D;

    // Baseline character growth from the current (not historical maximum) RPG level.
    private static final double LEVEL_HEALTH_PER_ROOT_LEVEL = 0.50D;
    private static final double LEVEL_ARMOR_PER_ROOT_LEVEL = 0.05D;

    // Allocated attribute growth.
    private static final double AGILITY_SPEED_PER_ROOT_POINT = 0.0015D;
    private static final double CONSTITUTION_HEALTH_PER_ROOT_POINT = 0.50D;
    private static final double CONSTITUTION_ARMOR_PER_ROOT_POINT = 0.08D;

    private static final ResourceLocation LEVEL_HEALTH_ID =
            ResourceLocation.fromNamespaceAndPath("rpgcore", "level_health");
    private static final ResourceLocation LEVEL_ARMOR_ID =
            ResourceLocation.fromNamespaceAndPath("rpgcore", "level_armor");
    private static final ResourceLocation AGILITY_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath("rpgcore", "agility_speed");
    private static final ResourceLocation CONSTITUTION_HEALTH_ID =
            ResourceLocation.fromNamespaceAndPath("rpgcore", "constitution_health");
    private static final ResourceLocation CONSTITUTION_ARMOR_ID =
            ResourceLocation.fromNamespaceAndPath("rpgcore", "constitution_armor");

    private RpgGameplayEffects() {
    }

    /**
     * Replaces this mod's transient modifiers with values derived from the
     * player's current server-side RPG data. Transient modifiers are rebuilt
     * on login/respawn and are never saved as duplicate permanent bonuses.
     */
    public static void apply(ServerPlayer player, PlayerRpgData data) {
        double levelScale = Math.min(Math.sqrt(Math.max(0, data.rpgLevel())), MAX_EFFECTIVE_LEVEL);
        double agilityScale = effectiveAttributePoints(data.agility());
        double constitutionScale = effectiveAttributePoints(data.constitution());

        replaceModifier(player, Attributes.MAX_HEALTH, LEVEL_HEALTH_ID,
                levelScale * LEVEL_HEALTH_PER_ROOT_LEVEL, AttributeModifier.Operation.ADD_VALUE);
        replaceModifier(player, Attributes.ARMOR, LEVEL_ARMOR_ID,
                levelScale * LEVEL_ARMOR_PER_ROOT_LEVEL, AttributeModifier.Operation.ADD_VALUE);

        replaceModifier(player, Attributes.MOVEMENT_SPEED, AGILITY_SPEED_ID,
                agilityScale * AGILITY_SPEED_PER_ROOT_POINT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        replaceModifier(player, Attributes.MAX_HEALTH, CONSTITUTION_HEALTH_ID,
                constitutionScale * CONSTITUTION_HEALTH_PER_ROOT_POINT, AttributeModifier.Operation.ADD_VALUE);
        replaceModifier(player, Attributes.ARMOR, CONSTITUTION_ARMOR_ID,
                constitutionScale * CONSTITUTION_ARMOR_PER_ROOT_POINT, AttributeModifier.Operation.ADD_VALUE);

        // Lowering Constitution or current level can lower max health. Clamp
        // current health so it never remains above the newly calculated maximum.
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }

    private static double effectiveAttributePoints(long points) {
        long safePoints = Math.max(0L, Math.min(points, ATTRIBUTE_POINTS_SOFT_CAP));
        return Math.min(Math.sqrt(safePoints), MAX_EFFECTIVE_ATTRIBUTE_POINTS);
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
