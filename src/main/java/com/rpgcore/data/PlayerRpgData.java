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
        long agility,
        long constitution,
        long intelligence,
        long spirit,
        boolean progressionInitialized
) {
    public static final PlayerRpgData EMPTY =
            new PlayerRpgData(0, 0, 0L, 0L, 0L, 0L, 0L, false);

    public static final Codec<PlayerRpgData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("rpg_level", 0).forGetter(PlayerRpgData::rpgLevel),
            Codec.INT.optionalFieldOf("highest_level_reached", 0).forGetter(PlayerRpgData::highestLevelReached),
            Codec.LONG.optionalFieldOf("strength", 0L).forGetter(PlayerRpgData::strength),
            Codec.LONG.optionalFieldOf("agility", 0L).forGetter(PlayerRpgData::agility),
            Codec.LONG.optionalFieldOf("constitution", 0L).forGetter(PlayerRpgData::constitution),
            Codec.LONG.optionalFieldOf("intelligence", 0L).forGetter(PlayerRpgData::intelligence),
            Codec.LONG.optionalFieldOf("spirit", 0L).forGetter(PlayerRpgData::spirit),
            Codec.BOOL.optionalFieldOf("progression_initialized", false).forGetter(PlayerRpgData::progressionInitialized)
    ).apply(instance, PlayerRpgData::new));

    public static final StreamCodec<ByteBuf, PlayerRpgData> STREAM_CODEC = StreamCodec.of(
            PlayerRpgData::writeToBuffer, PlayerRpgData::readFromBuffer
    );

    public PlayerRpgData {
        rpgLevel = clampLevel(rpgLevel);
        highestLevelReached = Math.max(clampLevel(highestLevelReached), rpgLevel);
        strength = nonNegative(strength);
        agility = nonNegative(agility);
        constitution = nonNegative(constitution);
        intelligence = nonNegative(intelligence);
        spirit = nonNegative(spirit);
    }

    private static int clampLevel(int level) {
        return Math.max(0, Math.min(RpgProgression.MAX_LEVEL, level));
    }

    public PlayerRpgData initializeProgression(int vanillaLevel) {
        int safeLevel = clampLevel(vanillaLevel);
        // Preserve previously allocated attributes and their earned-point baseline
        // when upgrading from the earlier prototype.
        int previousBaseline = Math.max(rpgLevel, highestLevelReached);
        return new PlayerRpgData(safeLevel, Math.max(previousBaseline, safeLevel),
                strength, agility, constitution, intelligence, spirit, true);
    }

    public PlayerRpgData withVanillaLevel(int vanillaLevel) {
        int safeLevel = clampLevel(vanillaLevel);
        return new PlayerRpgData(safeLevel, Math.max(highestLevelReached, safeLevel),
                strength, agility, constitution, intelligence, spirit, true);
    }

    public long allocatedAttributePoints() {
        long total = 0L;
        total = saturatedAdd(total, strength);
        total = saturatedAdd(total, agility);
        total = saturatedAdd(total, constitution);
        total = saturatedAdd(total, intelligence);
        return saturatedAdd(total, spirit);
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
            case AGILITY -> agility;
            case CONSTITUTION -> constitution;
            case INTELLIGENCE -> intelligence;
            case SPIRIT -> spirit;
        };
    }

    public PlayerRpgData addAttribute(PlayerAttribute attribute, long amount) {
        if (amount <= 0L) return this;
        long next = saturatedAdd(valueOf(attribute), amount);
        return switch (attribute) {
            case STRENGTH -> withAttributes(next, agility, constitution, intelligence, spirit);
            case AGILITY -> withAttributes(strength, next, constitution, intelligence, spirit);
            case CONSTITUTION -> withAttributes(strength, agility, next, intelligence, spirit);
            case INTELLIGENCE -> withAttributes(strength, agility, constitution, next, spirit);
            case SPIRIT -> withAttributes(strength, agility, constitution, intelligence, next);
        };
    }

    public PlayerRpgData resetAttributes() {
        return withAttributes(0L, 0L, 0L, 0L, 0L);
    }

    private PlayerRpgData withAttributes(long s, long a, long c, long i, long sp) {
        return new PlayerRpgData(rpgLevel, highestLevelReached, s, a, c, i, sp, progressionInitialized);
    }

    private static long nonNegative(long value) { return Math.max(0L, value); }

    private static long saturatedAdd(long a, long b) {
        return b > 0L && a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    private static void writeToBuffer(ByteBuf b, PlayerRpgData d) {
        b.writeInt(d.rpgLevel);
        b.writeInt(d.highestLevelReached);
        b.writeLong(d.strength);
        b.writeLong(d.agility);
        b.writeLong(d.constitution);
        b.writeLong(d.intelligence);
        b.writeLong(d.spirit);
        b.writeBoolean(d.progressionInitialized);
    }

    private static PlayerRpgData readFromBuffer(ByteBuf b) {
        return new PlayerRpgData(b.readInt(), b.readInt(), b.readLong(), b.readLong(),
                b.readLong(), b.readLong(), b.readLong(), b.readBoolean());
    }
}
