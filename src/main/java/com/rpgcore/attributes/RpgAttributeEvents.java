package com.rpgcore.attributes;

import com.rpgcore.data.ModAttachments;
import com.rpgcore.data.PlayerRpgData;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.Tags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.GetEquipmentDropChanceEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/** Server-side hooks for the RPG attributes that affect vanilla gameplay. */
public final class RpgAttributeEvents {
    private RpgAttributeEvents() {
    }

    /**
     * Strength is added as flat Minecraft damage points to player-caused hits.
     * Defense then reduces every damage source received by a player.
     */
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        float damage = event.getNewDamage();
        if (damage <= 0.0F) {
            return;
        }

        double strengthBonus = 0.0;
        ServerPlayer attacker = resolvePlayerAttacker(event.getSource());
        if (attacker != null) {
            PlayerRpgData attackerData = attacker.getData(ModAttachments.PLAYER_RPG_DATA.get());
            strengthBonus = AttributeFormulaCalculator.strengthDamage(attackerData.strength());
        }

        double defenseMultiplier = 1.0;
        if (event.getEntity() instanceof ServerPlayer defender) {
            PlayerRpgData defenderData = defender.getData(ModAttachments.PLAYER_RPG_DATA.get());
            double reduction = AttributeFormulaCalculator
                    .defenseReductionPercent(defenderData.defense());
            defenseMultiplier = 1.0 - reduction / 100.0;
        }

        if (strengthBonus > 0.0 || defenseMultiplier < 1.0) {
            // NeoForge fires this event after armor and potion reductions.
            // Strength is therefore a flat bonus at this final pre-health stage.
            double adjusted = (damage + strengthBonus) * defenseMultiplier;
            event.setNewDamage((float) Math.max(0.0, adjusted));
        }
    }

    /** Mining Speed multiplies the calculated break speed for every block. */
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        long points = player.getData(ModAttachments.PLAYER_RPG_DATA.get()).miningSpeed();
        double bonusPercent = AttributeFormulaCalculator.miningSpeedPercent(points);
        if (bonusPercent > 0.0) {
            event.setNewSpeed((float) (event.getNewSpeed() * (1.0 + bonusPercent / 100.0)));
        }
    }

    /**
     * Jump Height is converted into additional vertical launch energy. The
     * current jump velocity is preserved as the baseline, including effects
     * such as Jump Boost.
     */
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        long points = player.getData(ModAttachments.PLAYER_RPG_DATA.get()).jumpHeight();
        double extraHeight = AttributeFormulaCalculator.jumpHeightBlocks(points);
        if (extraHeight <= 0.0) {
            return;
        }

        Vec3 velocity = player.getDeltaMovement();
        if (velocity.y <= 0.0) {
            return;
        }

        double gravity = player.getAttributeValue(Attributes.GRAVITY);
        if (!Double.isFinite(gravity) || gravity <= 0.0) {
            gravity = 0.08;
        }

        double newY = Math.sqrt(velocity.y * velocity.y + 2.0 * gravity * extraHeight);
        player.setDeltaMovement(velocity.x, newY, velocity.z);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    /**
     * Luck adds extra items only to blocks tagged as ores. The dynamic
     * distribution is based on the player's current Luck points.
     */
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)
                || !event.getState().is(Tags.Blocks.ORES)
                || event.getDrops().isEmpty()) {
            return;
        }

        PlayerRpgData data = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
        int extra = AttributeFormulaCalculator.rollExtraMinerals(
                data.luck(), event.getLevel().getRandom());
        if (extra <= 0) {
            return;
        }

        for (ItemEntity droppedEntity : event.getDrops()) {
            ItemStack stack = droppedEntity.getItem();
            if (stack.isEmpty()) {
                continue;
            }

            // Silk Touch produces the ore block itself, not its mineral drop.
            if (stack.is(event.getState().getBlock().asItem())) {
                return;
            }

            stack.grow(extra);
            return;
        }
    }

    /**
     * The 1.21.265 API exposes a mutable probability for worn-equipment drops.
     * Apply the Luck percentage to the current chance, preserving its vanilla
     * and enchantment-derived value before multiplying it.
     */
    public static void onEquipmentDropChance(GetEquipmentDropChanceEvent event) {
        ServerPlayer attacker = resolvePlayerAttacker(event.getKillingBlow());
        if (attacker == null) {
            return;
        }

        long luckPoints = attacker.getData(ModAttachments.PLAYER_RPG_DATA.get()).luck();
        double bonusPercent =
                AttributeFormulaCalculator.luckEquipmentDropBonusPercent(luckPoints);
        if (bonusPercent <= 0.0) {
            return;
        }

        double adjustedChance = event.getChance() * (1.0 + bonusPercent / 100.0);
        event.setChance((float) Math.min(1.0, Math.max(0.0, adjustedChance)));
    }

    private static ServerPlayer resolvePlayerAttacker(DamageSource source) {
        Entity causingEntity = source.getEntity();
        if (causingEntity instanceof ServerPlayer player) {
            return player;
        }
        if (causingEntity instanceof Projectile projectile
                && projectile.getOwner() instanceof ServerPlayer player) {
            return player;
        }

        Entity directEntity = source.getDirectEntity();
        if (directEntity instanceof ServerPlayer player) {
            return player;
        }
        if (directEntity instanceof Projectile projectile
                && projectile.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }
}
