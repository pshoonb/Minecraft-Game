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
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
 * <p>Online participants are evaluated using their current attribute at the
 * target's death. When a participant logs out, their latest attribute is copied
 * into each active participation record so they remain eligible for the full
 * 60-second window.</p>
 *
 * <p>Each LivingExperienceDropEvent is treated as one vanilla XP award batch.
 * This is important for entities such as the Ender Dragon, which release XP in
 * multiple batches during their death animation.</p>
 */
public final class CombatParticipationTracker {
    public static final long PARTICIPATION_TIMEOUT_TICKS = 60L * 20L;

    private static final Map<LivingEntity, Map<UUID, ParticipantRecord>> PARTICIPANTS =
            new WeakHashMap<>();
    private static final Map<LivingEntity, Long> DEATH_EXPERIENCE_LOOT =
            new WeakHashMap<>();

    private CombatParticipationTracker() {
    }

    /** Last successful hit time plus the participant's most recently known online attribute. */
    private record ParticipantRecord(long lastDamageGameTime, long lastKnownExperienceLoot) {
        private ParticipantRecord withExperienceLoot(long experienceLoot) {
            return new ParticipantRecord(lastDamageGameTime, Math.max(0L, experienceLoot));
        }
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
        Map<UUID, ParticipantRecord> participants =
                PARTICIPANTS.computeIfAbsent(victim, ignored -> new HashMap<>());

        // Expire stale players before refreshing this attacker's record.
        participants.entrySet().removeIf(entry ->
                now - entry.getValue().lastDamageGameTime() >= PARTICIPATION_TIMEOUT_TICKS);

        PlayerRpgData data = attacker.getData(ModAttachments.PLAYER_RPG_DATA.get());
        participants.put(attacker.getUUID(),
                new ParticipantRecord(now, data.experienceLoot()));
    }

    /**
     * Capture the latest attribute before the player is removed from the
     * server's player list. NeoForge fires PlayerLoggedOutEvent before saving
     * and removing the ServerPlayer from PlayerList.
     */
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerId = player.getUUID();
        long latestExperienceLoot =
                player.getData(ModAttachments.PLAYER_RPG_DATA.get()).experienceLoot();

        for (Map<UUID, ParticipantRecord> participants : PARTICIPANTS.values()) {
            ParticipantRecord record = participants.get(playerId);
            if (record != null) {
                participants.put(playerId, record.withExperienceLoot(latestExperienceLoot));
            }
        }
    }

    /**
     * Snapshot the highest eligible participant's current/latest-known
     * attribute at the moment the living entity dies. Online players are read
     * live; offline players use the value captured by their logout event.
     * Keep the snapshot until the entity leaves the level to support multiple
     * XP batches such as the Ender Dragon.
     */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;
        }

        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Map<UUID, ParticipantRecord> participants = PARTICIPANTS.get(victim);
        if (participants == null || participants.isEmpty()) {
            DEATH_EXPERIENCE_LOOT.remove(victim);
            return;
        }

        long now = serverLevel.getGameTime();
        participants.entrySet().removeIf(entry ->
                now - entry.getValue().lastDamageGameTime() >= PARTICIPATION_TIMEOUT_TICKS);

        if (participants.isEmpty()) {
            PARTICIPANTS.remove(victim);
            DEATH_EXPERIENCE_LOOT.remove(victim);
            return;
        }

        long highestExperienceLoot = 0L;
        for (Map.Entry<UUID, ParticipantRecord> entry : participants.entrySet()) {
            UUID participantId = entry.getKey();
            long participantExperienceLoot =
                    entry.getValue().lastKnownExperienceLoot();

            ServerPlayer onlineParticipant =
                    serverLevel.getServer().getPlayerList().getPlayer(participantId);
            if (onlineParticipant != null
                    && onlineParticipant.getServer() == serverLevel.getServer()) {
                // A player who is still online may have changed their attributes
                // since their last hit, so read their live kill-time value.
                participantExperienceLoot = onlineParticipant
                        .getData(ModAttachments.PLAYER_RPG_DATA.get())
                        .experienceLoot();
            }
            // Otherwise retain the most recent value captured on hit/logout.

            highestExperienceLoot = Math.max(
                    highestExperienceLoot, participantExperienceLoot);
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
        // Respect an earlier cancellation by another mod; canceled XP must
        // remain canceled and must not be recreated by Experience Loot.
        if (event.isCanceled()) {
            return;
        }

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
        Iterator<Map.Entry<LivingEntity, Map<UUID, ParticipantRecord>>> entityIterator =
                PARTICIPANTS.entrySet().iterator();

        while (entityIterator.hasNext()) {
            Map.Entry<LivingEntity, Map<UUID, ParticipantRecord>> entityEntry =
                    entityIterator.next();
            LivingEntity entity = entityEntry.getKey();
            if (entity == null || entity.isRemoved()) {
                entityIterator.remove();
                DEATH_EXPERIENCE_LOOT.remove(entity);
                continue;
            }

            long now = entity.level().getGameTime();
            Map<UUID, ParticipantRecord> participants = entityEntry.getValue();
            participants.entrySet().removeIf(entry ->
                    now - entry.getValue().lastDamageGameTime()
                            >= PARTICIPATION_TIMEOUT_TICKS);

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
