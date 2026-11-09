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
        long health,
        long defense,
        long miningSpeed,
        long luck,
        long speed,
        long jumpHeight,
        boolean progressionInitialized
) {
    public static final PlayerRpgData EMPTY =
            new PlayerRpgData(0, 0, 0L, 0L, 0L, 0L, 0L, 0L, 0L, false);

    public static final Codec<PlayerRpgData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("rpg_level", 0).forGetter(PlayerRpgData::rpgLevel),
            Codec.INT.optionalFieldOf("highest_level_reached", 0).forGetter(PlayerRpgData::highestLevelReached),
            Codec.LONG.optionalFieldOf("strength", 0L).forGetter(PlayerRpgData::strength),
            Codec.LONG.optionalFieldOf("health", 0L).forGetter(PlayerRpgData::health),
            Codec.LONG.optionalFieldOf("defense", 0L).forGetter(PlayerRpgData::defense),
            Codec.LONG.optionalFieldOf("mining_speed", 0L).forGetter(PlayerRpgData::miningSpeed),
            Codec.LONG.optionalFieldOf("luck", 0L).forGetter(PlayerRpgData::luck),
            Codec.LONG.optionalFieldOf("speed", 0L).forGetter(PlayerRpgData::speed),
            Codec.LONG.optionalFieldOf("jump_height", 0L).forGetter(PlayerRpgData::jumpHeight),
            Codec.BOOL.optionalFieldOf("progression_initialized", false).forGetter(PlayerRpgData::progressionInitialized)
    ).apply(instance, PlayerRpgData::new));

    public static final StreamCodec<ByteBuf, PlayerRpgData> STREAM_CODEC = StreamCodec.of(
            PlayerRpgData::writeToBuffer, PlayerRpgData::readFromBuffer
    );

    public PlayerRpgData {
        rpgLevel = clampLevel(rpgLevel);
        highestLevelReached = Math.max(clampLevel(highestLevelReached), rpgLevel);
        strength = nonNegative(strength);
        health = nonNegative(health);
        defense = nonNegative(defense);
        miningSpeed = nonNegative(miningSpeed);
        luck = nonNegative(luck);
        speed = nonNegative(speed);
        jumpHeight = nonNegative(jumpHeight);
    }

    private static int clampLevel(int level) {
        return Math.max(0, Math.min(RpgProgression.MAX_LEVEL, level));
    }

    public PlayerRpgData initializeProgression(int vanillaLevel) {
        int safeLevel = clampLevel(vanillaLevel);
        // Preserve the highest level ever reached when upgrading existing worlds.
        int previousBaseline = Math.max(rpgLevel, highestLevelReached);
        return new PlayerRpgData(safeLevel, Math.max(previousBaseline, safeLevel),
                strength, health, defense, miningSpeed, luck, speed, jumpHeight, true);
    }

    public PlayerRpgData withVanillaLevel(int vanillaLevel) {
        int safeLevel = clampLevel(vanillaLevel);
        return new PlayerRpgData(safeLevel, Math.max(highestLevelReached, safeLevel),
                strength, health, defense, miningSpeed, luck, speed, jumpHeight, true);
    }

    public long allocatedAttributePoints() {
        long total = 0L;
        total = saturatedAdd(total, strength);
        total = saturatedAdd(total, health);
        total = saturatedAdd(total, defense);
        total = saturatedAdd(total, miningSpeed);
        total = saturatedAdd(total, luck);
        total = saturatedAdd(total, speed);
        return saturatedAdd(total, jumpHeight);
    }

    /** Attribute rewards are based on the highest vanilla level ever reached. */
    public long totalAttributePoints() {
        return RpgProgression.totalAttributePointsAtLevel(highestLevelReached);
    }

    public long availableAttributePoints() {
        return Math.max(0L, totalAttributePoints() - allocatedAttributePoints());
    }

    public long valueOf(PlayerAttribute attribute) {
        return switch (attribute) {
            case STRENGTH -> strength;
            case HEALTH -> health;
            case DEFENSE -> defense;
            case MINING_SPEED -> miningSpeed;
            case LUCK -> luck;
            case SPEED -> speed;
            case JUMP_HEIGHT -> jumpHeight;
        };
    }

    public PlayerRpgData addAttribute(PlayerAttribute attribute, long amount) {
        if (amount <= 0L) return this;
        long next = saturatedAdd(valueOf(attribute), amount);
        return switch (attribute) {
            case STRENGTH -> withAttributes(next, health, defense, miningSpeed, luck, speed, jumpHeight);
            case HEALTH -> withAttributes(strength, next, defense, miningSpeed, luck, speed, jumpHeight);
            case DEFENSE -> withAttributes(strength, health, next, miningSpeed, luck, speed, jumpHeight);
            case MINING_SPEED -> withAttributes(strength, health, defense, next, luck, speed, jumpHeight);
            case LUCK -> withAttributes(strength, health, defense, miningSpeed, next, speed, jumpHeight);
            case SPEED -> withAttributes(strength, health, defense, miningSpeed, luck, next, jumpHeight);
            case JUMP_HEIGHT -> withAttributes(strength, health, defense, miningSpeed, luck, speed, next);
        };
    }

    public PlayerRpgData resetAttributes() {
        return withAttributes(0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    private PlayerRpgData withAttributes(long s, long h, long d, long ms,
                                         long l, long sp, long jh) {
        return new PlayerRpgData(rpgLevel, highestLevelReached,
                s, h, d, ms, l, sp, jh, progressionInitialized);
    }

    private static long nonNegative(long value) {
        return Math.max(0L, value);
    }

    private static long saturatedAdd(long a, long b) {
        return b > 0L && a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    private static void writeToBuffer(ByteBuf b, PlayerRpgData d) {
        b.writeInt(d.rpgLevel);
        b.writeInt(d.highestLevelReached);
        b.writeLong(d.strength);
        b.writeLong(d.health);
        b.writeLong(d.defense);
        b.writeLong(d.miningSpeed);
        b.writeLong(d.luck);
        b.writeLong(d.speed);
        b.writeLong(d.jumpHeight);
        b.writeBoolean(d.progressionInitialized);
    }

    private static PlayerRpgData readFromBuffer(ByteBuf b) {
        return new PlayerRpgData(b.readInt(), b.readInt(),
                b.readLong(), b.readLong(), b.readLong(), b.readLong(),
                b.readLong(), b.readLong(), b.readLong(), b.readBoolean());
    }
}
