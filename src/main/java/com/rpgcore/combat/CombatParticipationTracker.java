package com.rpgcore.combat;

import com.rpgcore.data.ModAttachments;
import com.rpgcore.data.PlayerRpgData;
import com.rpgcore.experience.ExperienceLootCalculator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Tracks active combat participants and takes a kill-time Experience Loot snapshot.
 *
 * <p>Participation is transient server state. A player's timer is refreshed by
 * every successfully applied health hit and expires after 1,200 game ticks.</p>
 *
 * <p>Each LivingExperienceDropEvent is treated as one vanilla XP award batch.
 * This is important for entities such as the Ender Dragon, which release XP in
 * multiple batches during their death animation.</p>
 */
public final class CombatParticipationTracker {
    public static final long PARTICIPATION_TIMEOUT_TICKS = 60L * 20L;

    private static final Map<LivingEntity, Map<UUID, Long>> PARTICIPANTS =
            new WeakHashMap<>();
    private static final Map<LivingEntity, Long> DEATH_EXPERIENCE_LOOT =
            new WeakHashMap<>();

    private CombatParticipationTracker() {
    }

    /** Record only damage that was actually applied to the victim's health. */
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0.0F) {
            return;
        }

        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ServerPlayer attacker = resolvePlayerAttacker(event.getSource());
        if (attacker == null || attacker.getServer() != serverLevel.getServer()) {
            return;
        }

        long now = serverLevel.getGameTime();
        Map<UUID, Long> participants =
                PARTICIPANTS.computeIfAbsent(victim, ignored -> new HashMap<>());

        // Expire old participants before refreshing this attacker's timer.
        participants.entrySet().removeIf(entry ->
                now - entry.getValue() >= PARTICIPATION_TIMEOUT_TICKS);
        participants.put(attacker.getUUID(), now);
    }

    /**
     * Snapshot the highest eligible online participant's current attribute at
     * the moment the living entity dies. Keeping this snapshot until the entity
     * leaves the level supports multi-batch XP drops such as the Ender Dragon.
     */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;
        }

        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Map<UUID, Long> participants = PARTICIPANTS.get(victim);
        if (participants == null || participants.isEmpty()) {
            return;
        }

        long now = serverLevel.getGameTime();
        participants.entrySet().removeIf(entry ->
                now - entry.getValue() >= PARTICIPATION_TIMEOUT_TICKS);

        if (participants.isEmpty()) {
            PARTICIPANTS.remove(victim);
            return;
        }

        long highestExperienceLoot = 0L;
        for (UUID participantId : participants.keySet()) {
            // As in the original multiplayer rule, only participants still
            // online at the kill are eligible for this server-side snapshot.
            ServerPlayer participant =
                    serverLevel.getServer().getPlayerList().getPlayer(participantId);
            if (participant == null || participant.getServer() != serverLevel.getServer()) {
                continue;
            }

            PlayerRpgData data =
                    participant.getData(ModAttachments.PLAYER_RPG_DATA.get());
            highestExperienceLoot = Math.max(
                    highestExperienceLoot, data.experienceLoot());
        }

        if (highestExperienceLoot > 0L) {
            DEATH_EXPERIENCE_LOOT.put(victim, highestExperienceLoot);
        } else {
            DEATH_EXPERIENCE_LOOT.remove(victim);
        }
    }

    /**
     * Replaces only this event's vanilla XP batch with custom-denomination
     * orbs. No nearby or previously existing XP orb is searched or modified.
     */
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Long experienceLootPoints = DEATH_EXPERIENCE_LOOT.get(victim);
        int batchExperience = event.getDroppedExperience();

        if (experienceLootPoints == null
                || experienceLootPoints <= 0L
                || batchExperience <= 0) {
            return;
        }

        long originalOrbCount =
                ExperienceLootCalculator.countVanillaOrbUnits(batchExperience);
        if (originalOrbCount <= 0L) {
            return;
        }

        int newOrbValue = ExperienceLootCalculator.calculateOrbValue(
                batchExperience, originalOrbCount, experienceLootPoints);
        if (newOrbValue <= 0) {
            return;
        }

        // The event normally feeds the vanilla XP-orb award method. Cancel it
        // so the original batch is not duplicated, then create exactly N
        // logical orbs with the same new denomination Q.
        event.setCanceled(true);

        for (long index = 0; index < originalOrbCount; index++) {
            ExperienceOrb orb = new ExperienceOrb(
                    serverLevel,
                    victim.getX(),
                    victim.getY(),
                    victim.getZ(),
                    newOrbValue);
            serverLevel.addFreshEntity(orb);
        }
    }

    /** Remove both tracking records when the entity actually leaves its level. */
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)
                || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        PARTICIPANTS.remove(victim);
        DEATH_EXPERIENCE_LOOT.remove(victim);
    }

    /** Periodically expire abandoned participation data, even if the mob lives on. */
    public static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Map.Entry<LivingEntity, Map<UUID, Long>>> entityIterator =
                PARTICIPANTS.entrySet().iterator();

        while (entityIterator.hasNext()) {
            Map.Entry<LivingEntity, Map<UUID, Long>> entityEntry = entityIterator.next();
            LivingEntity entity = entityEntry.getKey();
            if (entity == null || entity.isRemoved()) {
                entityIterator.remove();
                DEATH_EXPERIENCE_LOOT.remove(entity);
                continue;
            }

            long now = entity.level().getGameTime();
            Map<UUID, Long> participants = entityEntry.getValue();
            participants.entrySet().removeIf(entry ->
                    now - entry.getValue() >= PARTICIPATION_TIMEOUT_TICKS);

            if (participants.isEmpty()) {
                entityIterator.remove();
            }
        }
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
