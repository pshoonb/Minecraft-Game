package com.rpgcore.combat;

import com.rpgcore.data.ModAttachments;
import com.rpgcore.data.PlayerRpgData;
import com.rpgcore.experience.ExperienceLootCalculator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Tracks players who successfully damaged each living entity.
 *
 * <p>Participation is transient server state: it is never written to entity
 * saves, and each player is represented by UUID to avoid retaining player
 * objects. An entry expires after 1,200 game ticks (60 seconds).</p>
 */
public final class CombatParticipationTracker {
    public static final long PARTICIPATION_TIMEOUT_TICKS = 60L * 20L;

    private static final Map<LivingEntity, Map<UUID, Long>> PARTICIPANTS =
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

        // Remove stale records before refreshing this attacker's timestamp.
        participants.entrySet().removeIf(entry ->
                now - entry.getValue() >= PARTICIPATION_TIMEOUT_TICKS);
        participants.put(attacker.getUUID(), now);
    }

    /**
     * Modify the vanilla XP-drop event instead of spawning additional orbs.
     * This preserves the normal drop pipeline and prevents duplicate XP.
     */
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Map<UUID, Long> participants = PARTICIPANTS.remove(victim);
        int currentDrop = event.getDroppedExperience();

        // Do not create XP for entities whose original drop is zero.
        if (participants == null
                || participants.isEmpty()
                || event.getOriginalExperience() <= 0
                || currentDrop <= 0) {
            return;
        }

        long now = serverLevel.getGameTime();
        long highestExperienceLoot = 0L;

        for (Map.Entry<UUID, Long> entry : participants.entrySet()) {
            if (now - entry.getValue() >= PARTICIPATION_TIMEOUT_TICKS) {
                continue;
            }

            // The value is read at kill time so current player attributes apply.
            // Offline players cannot be resolved from the live server player list.
            ServerPlayer participant =
                    serverLevel.getServer().getPlayerList().getPlayer(entry.getKey());
            if (participant == null) {
                continue;
            }

            PlayerRpgData data =
                    participant.getData(ModAttachments.PLAYER_RPG_DATA.get());
            highestExperienceLoot = Math.max(
                    highestExperienceLoot, data.experienceLoot());
        }

        if (highestExperienceLoot <= 0L) {
            return;
        }

        event.setDroppedExperience(
                ExperienceLootCalculator.applyBonus(currentDrop, highestExperienceLoot));
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
