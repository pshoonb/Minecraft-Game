package com.rpgcore.progression;

import com.rpgcore.data.PlayerRpgData;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies the seven allocated RPG attributes to vanilla player mechanics.
 * Attribute growth is linear for the first 25 effective points, then follows
 * a logarithmic soft cap that never hard-caps.
 */
public final class RpgGameplayEffects {
    private static final double SOFT_CAP_START = 25.0D;
    private static final double SOFT_CAP_SCALE = 15.0D;

    // Rebalanced for early feedback and the practical level 100-150 range.
    private static final double STRENGTH_DAMAGE_PER_POINT = 0.20D;
    private static final double HEALTH_PER_POINT = 0.35D;
    private static final double DEFENSE_ARMOR_PER_POINT = 0.30D;
    private static final double MINING_SPEED_PER_POINT = 0.02D;
    private static final double MINING_EFFICIENCY_PER_POINT = 0.30D;
    private static final double LUCK_PER_POINT = 0.20D;
    private static final double SPEED_PER_POINT = 0.012D;
    private static final double JUMP_VELOCITY_PER_POINT = 0.015D;

    private static final double MAX_UNIVERSAL_DAMAGE_REDUCTION = 0.40D;
    private static final double MAX_BURNING_TIME_REDUCTION = 0.60D;
    private static final double MAX_EXPLOSION_KNOCKBACK_RESISTANCE = 0.70D;
    private static final double MAX_FALL_DAMAGE_REDUCTION = 0.75D;

    private static final ResourceLocation STRENGTH_DAMAGE_ID = id("strength_damage");
    private static final ResourceLocation HEALTH_MAX_HEALTH_ID = id("health_max_health");
    private static final ResourceLocation DEFENSE_ARMOR_ID = id("defense_armor");
    private static final ResourceLocation DEFENSE_BURNING_TIME_ID = id("defense_burning_time");
    private static final ResourceLocation DEFENSE_EXPLOSION_KNOCKBACK_ID = id("defense_explosion_knockback");
    private static final ResourceLocation MINING_SPEED_ID = id("mining_speed");
    private static final ResourceLocation MINING_EFFICIENCY_ID = id("mining_efficiency");
    private static final ResourceLocation LUCK_ID = id("luck");
    private static final ResourceLocation SPEED_ID = id("speed");
    private static final ResourceLocation JUMP_SAFE_FALL_DISTANCE_ID = id("jump_safe_fall_distance");
    private static final ResourceLocation JUMP_FALL_DAMAGE_ID = id("jump_fall_damage");

    private RpgGameplayEffects() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("rpgcore", path);
    }

    /** Apply stable modifiers on both client and server, replacing rather than stacking. */
    public static void apply(Player player, PlayerRpgData data) {
        double strength = effectiveAttributePoints(data.strength());
        double health = effectiveAttributePoints(data.health());
        double defense = effectiveAttributePoints(data.defense());
        double miningSpeed = effectiveAttributePoints(data.miningSpeed());
        double luck = effectiveAttributePoints(data.luck());
        double speed = effectiveAttributePoints(data.speed());
        double jump = effectiveAttributePoints(data.jumpHeight());

        replaceModifier(player, Attributes.ATTACK_DAMAGE, STRENGTH_DAMAGE_ID,
                strength * STRENGTH_DAMAGE_PER_POINT, AttributeModifier.Operation.ADD_VALUE);
        replaceModifier(player, Attributes.MAX_HEALTH, HEALTH_MAX_HEALTH_ID,
                health * HEALTH_PER_POINT, AttributeModifier.Operation.ADD_VALUE);

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

        // Jump Height and fall protection share the same point investment.
        replaceModifier(player, Attributes.SAFE_FALL_DISTANCE, JUMP_SAFE_FALL_DISTANCE_ID,
                jump * 0.75D, AttributeModifier.Operation.ADD_VALUE);
        double fallReduction = MAX_FALL_DAMAGE_REDUCTION * jump / (jump + 15.0D);
        replaceModifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, JUMP_FALL_DAMAGE_ID,
                -fallReduction, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }

    /** Reduce final damage after vanilla armor and enchantment handling. */
    public static float damageMultiplier(long defensePoints) {
        double defense = effectiveAttributePoints(defensePoints);
        double reduction = MAX_UNIVERSAL_DAMAGE_REDUCTION * defense / (defense + 50.0D);
        return (float) (1.0D - reduction);
    }

    /** Increase launch velocity on the actual jump event. Called on client and server. */
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
     * Add resource copies after vanilla ore loot (including Fortune) was calculated.
     * Silk-touch ore-block drops are not duplicated.
     */
    public static void applyMiningLuckDrops(BlockDropsEvent event, PlayerRpgData data) {
        if (!isSupportedOre(event.getState())) {
            return;
        }

        double luck = effectiveAttributePoints(data.luck());
        if (luck <= 0.0D) {
            return;
        }

        // One guaranteed extra roll per 40 effective Luck, plus fractional chance.
        int extraRolls = (int) (luck / 40.0D);
        double fractionalChance = (luck - extraRolls * 40.0D) / 40.0D;
        if (event.getLevel().getRandom().nextDouble() < fractionalChance) {
            extraRolls++;
        }
        if (extraRolls <= 0) {
            return;
        }

        List<ItemEntity> originalDrops = new ArrayList<>(event.getDrops());
        for (ItemEntity originalDrop : originalDrops) {
            ItemStack originalStack = originalDrop.getItem();
            if (!isSupportedOreResource(originalStack)) {
                continue;
            }

            for (int roll = 0; roll < extraRolls; roll++) {
                ItemEntity bonusDrop = new ItemEntity(event.getLevel(),
                        originalDrop.getX(), originalDrop.getY(), originalDrop.getZ(),
                        originalStack.copy());
                bonusDrop.setDefaultPickUpDelay();
                if (event.getBreaker() != null) {
                    bonusDrop.setThrower(event.getBreaker());
                }
                event.getDrops().add(bonusDrop);
            }
        }
    }

    private static boolean isSupportedOre(BlockState state) {
        return state.is(BlockTags.COAL_ORES)
                || state.is(BlockTags.DIAMOND_ORES)
                || state.is(BlockTags.EMERALD_ORES)
                || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.REDSTONE_ORES)
                || state.is(BlockTags.COPPER_ORES)
                || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.GOLD_ORES)
                || state.is(Blocks.NETHER_QUARTZ_ORE)
                || state.is(Blocks.ANCIENT_DEBRIS);
    }

    private static boolean isSupportedOreResource(ItemStack stack) {
        return stack.is(Items.COAL)
                || stack.is(Items.DIAMOND)
                || stack.is(Items.EMERALD)
                || stack.is(Items.LAPIS_LAZULI)
                || stack.is(Items.REDSTONE)
                || stack.is(Items.RAW_COPPER)
                || stack.is(Items.RAW_IRON)
                || stack.is(Items.RAW_GOLD)
                || stack.is(Items.GOLD_NUGGET)
                || stack.is(Items.QUARTZ)
                || stack.is(Items.ANCIENT_DEBRIS);
    }

    /** The first 25 points are linear; later points have diminishing, never-zero returns. */
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
