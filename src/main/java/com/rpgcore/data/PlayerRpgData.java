package com.rpgcore.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rpgcore.progression.RpgProgression;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Persistent per-player RPG attributes, synchronized with the vanilla player level. */
public record PlayerRpgData(
        int rpgLevel,
        int highestLevelReached,
        long strength,
        long defense,
        long health,
        long miningSpeed,
        long speed,
        long jumpHeight,
        long luck,
        long experienceLoot,
        long waterBreathing,
        boolean progressionInitialized,
        boolean teleportUnlocked
) {
    public static final PlayerRpgData EMPTY =
            new PlayerRpgData(0, 0, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, false, false);

    public static final Codec<PlayerRpgData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("rpg_level", 0).forGetter(PlayerRpgData::rpgLevel),
            Codec.INT.optionalFieldOf("highest_level_reached", 0).forGetter(PlayerRpgData::highestLevelReached),
            Codec.LONG.optionalFieldOf("strength", 0L).forGetter(PlayerRpgData::strength),
            Codec.LONG.optionalFieldOf("defense", 0L).forGetter(PlayerRpgData::defense),
            Codec.LONG.optionalFieldOf("health", 0L).forGetter(PlayerRpgData::health),
            Codec.LONG.optionalFieldOf("mining_speed", 0L).forGetter(PlayerRpgData::miningSpeed),
            Codec.LONG.optionalFieldOf("speed", 0L).forGetter(PlayerRpgData::speed),
            Codec.LONG.optionalFieldOf("jump_height", 0L).forGetter(PlayerRpgData::jumpHeight),
            Codec.LONG.optionalFieldOf("luck", 0L).forGetter(PlayerRpgData::luck),
            Codec.LONG.optionalFieldOf("experience_loot", 0L).forGetter(PlayerRpgData::experienceLoot),
            Codec.LONG.optionalFieldOf("water_breathing", 0L).forGetter(PlayerRpgData::waterBreathing),
            Codec.BOOL.optionalFieldOf("progression_initialized", false).forGetter(PlayerRpgData::progressionInitialized),
            Codec.BOOL.optionalFieldOf("teleport_unlocked", false).forGetter(PlayerRpgData::teleportUnlocked)
    ).apply(instance, PlayerRpgData::new));

    public static final StreamCodec<ByteBuf, PlayerRpgData> STREAM_CODEC = StreamCodec.of(
            PlayerRpgData::writeToBuffer, PlayerRpgData::readFromBuffer
    );

    public PlayerRpgData {
        rpgLevel = clampLevel(rpgLevel);
        highestLevelReached = Math.max(clampLevel(highestLevelReached), rpgLevel);
        strength = nonNegative(strength);
        defense = nonNegative(defense);
        health = nonNegative(health);
        miningSpeed = nonNegative(miningSpeed);
        speed = nonNegative(speed);
        jumpHeight = nonNegative(jumpHeight);
        luck = nonNegative(luck);
        experienceLoot = nonNegative(experienceLoot);
        waterBreathing = nonNegative(waterBreathing);
    }

    private static int clampLevel(int level) {
        return Math.max(0, Math.min(RpgProgression.MAX_LEVEL, level));
    }

    public PlayerRpgData initializeProgression(int vanillaLevel) {
        int safeLevel = clampLevel(vanillaLevel);
        int previousBaseline = Math.max(rpgLevel, highestLevelReached);
        return new PlayerRpgData(safeLevel, Math.max(previousBaseline, safeLevel),
                strength, defense, health, miningSpeed, speed, jumpHeight, luck,
                experienceLoot, waterBreathing, true, teleportUnlocked);
    }

    public PlayerRpgData withVanillaLevel(int vanillaLevel) {
        int safeLevel = clampLevel(vanillaLevel);
        return new PlayerRpgData(safeLevel, Math.max(highestLevelReached, safeLevel),
                strength, defense, health, miningSpeed, speed, jumpHeight, luck,
                experienceLoot, waterBreathing, true, teleportUnlocked);
    }

    public long allocatedAttributePoints() {
        long total = 0L;
        total = saturatedAdd(total, strength);
        total = saturatedAdd(total, defense);
        total = saturatedAdd(total, health);
        total = saturatedAdd(total, miningSpeed);
        total = saturatedAdd(total, speed);
        total = saturatedAdd(total, jumpHeight);
        total = saturatedAdd(total, luck);
        total = saturatedAdd(total, experienceLoot);
        return saturatedAdd(total, waterBreathing);
    }

    public long totalAttributePoints() {
        return RpgProgression.totalAttributePointsAtLevel(highestLevelReached);
    }

    public long availableAttributePoints() {
        long purchaseCost = teleportUnlocked ? TELEPORT_UNLOCK_COST : 0L;
        return Math.max(0L, totalAttributePoints() - allocatedAttributePoints() - purchaseCost);
    }

    public static final long TELEPORT_UNLOCK_COST = 10_000L;

    /** Permanently unlocks teleport; the cost is reflected in availableAttributePoints(). */
    public PlayerRpgData withTeleportUnlocked() {
        if (teleportUnlocked) return this;
        return new PlayerRpgData(rpgLevel, highestLevelReached, strength, defense, health,
                miningSpeed, speed, jumpHeight, luck, experienceLoot, waterBreathing,
                progressionInitialized, true);
    }

    public long valueOf(PlayerAttribute attribute) {
        return switch (attribute) {
            case STRENGTH -> strength;
            case DEFENSE -> defense;
            case HEALTH -> health;
            case MINING_SPEED -> miningSpeed;
            case SPEED -> speed;
            case JUMP_HEIGHT -> jumpHeight;
            case LUCK -> luck;
            case EXPERIENCE_LOOT -> experienceLoot;
            case WATER_BREATHING -> waterBreathing;
        };
    }

    public PlayerRpgData addAttribute(PlayerAttribute attribute, long amount) {
        if (attribute == null || amount <= 0L) return this;
        return withAttributeValue(attribute, saturatedAdd(valueOf(attribute), amount));
    }

    /** Removes up to the requested number of points without allowing negatives. */
    public PlayerRpgData removeAttribute(PlayerAttribute attribute, long amount) {
        if (attribute == null || amount <= 0L) return this;
        long current = valueOf(attribute);
        long next = current - Math.min(current, amount);
        return withAttributeValue(attribute, next);
    }

    private PlayerRpgData withAttributeValue(PlayerAttribute attribute, long value) {
        return switch (attribute) {
            case STRENGTH -> withAttributes(value, defense, health, miningSpeed, speed, jumpHeight, luck, experienceLoot, waterBreathing);
            case DEFENSE -> withAttributes(strength, value, health, miningSpeed, speed, jumpHeight, luck, experienceLoot, waterBreathing);
            case HEALTH -> withAttributes(strength, defense, value, miningSpeed, speed, jumpHeight, luck, experienceLoot, waterBreathing);
            case MINING_SPEED -> withAttributes(strength, defense, health, value, speed, jumpHeight, luck, experienceLoot, waterBreathing);
            case SPEED -> withAttributes(strength, defense, health, miningSpeed, value, jumpHeight, luck, experienceLoot, waterBreathing);
            case JUMP_HEIGHT -> withAttributes(strength, defense, health, miningSpeed, speed, value, luck, experienceLoot, waterBreathing);
            case LUCK -> withAttributes(strength, defense, health, miningSpeed, speed, jumpHeight, value, experienceLoot, waterBreathing);
            case EXPERIENCE_LOOT -> withAttributes(strength, defense, health, miningSpeed, speed, jumpHeight, luck, value, waterBreathing);
            case WATER_BREATHING -> withAttributes(strength, defense, health, miningSpeed, speed, jumpHeight, luck, experienceLoot, value);
        };
    }

    public PlayerRpgData resetAttributes() {
        return withAttributes(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    private PlayerRpgData withAttributes(
            long s, long d, long h, long m, long sp, long j, long l,
            long expLoot, long waterBreathing) {
        return new PlayerRpgData(rpgLevel, highestLevelReached,
                s, d, h, m, sp, j, l, expLoot, waterBreathing, progressionInitialized, teleportUnlocked);
    }

    private static long nonNegative(long value) { return Math.max(0L, value); }

    private static long saturatedAdd(long a, long b) {
        return b > 0L && a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    private static void writeToBuffer(ByteBuf b, PlayerRpgData d) {
        b.writeInt(d.rpgLevel);
        b.writeInt(d.highestLevelReached);
        b.writeLong(d.strength);
        b.writeLong(d.defense);
        b.writeLong(d.health);
        b.writeLong(d.miningSpeed);
        b.writeLong(d.speed);
        b.writeLong(d.jumpHeight);
        b.writeLong(d.luck);
        b.writeLong(d.experienceLoot);
        b.writeLong(d.waterBreathing);
        b.writeBoolean(d.progressionInitialized);
        b.writeBoolean(d.teleportUnlocked);
    }

    private static PlayerRpgData readFromBuffer(ByteBuf b) {
        return new PlayerRpgData(b.readInt(), b.readInt(),
                b.readLong(), b.readLong(), b.readLong(), b.readLong(),
                b.readLong(), b.readLong(), b.readLong(), b.readLong(), b.readLong(),
                b.readBoolean(), b.readBoolean());
    }
}
