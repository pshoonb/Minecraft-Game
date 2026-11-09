package com.rpgcore.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rpgcore.progression.RpgProgression;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Persistent, per-player RPG state. All mutations are server-owned. */
public record PlayerRpgData(
        int rpgLevel,
        long experienceProgress,
        long totalExperience,
        long strength,
        long agility,
        long constitution,
        long intelligence,
        long spirit,
        boolean progressionInitialized,
        int diagnosticCounter
) {
    public static final PlayerRpgData EMPTY =
            new PlayerRpgData(0, 0L, 0L, 0L, 0L, 0L, 0L, 0L, false, 0);

    public static final Codec<PlayerRpgData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("rpg_level", 0).forGetter(PlayerRpgData::rpgLevel),
            Codec.LONG.optionalFieldOf("rpg_experience_progress", 0L).forGetter(PlayerRpgData::experienceProgress),
            Codec.LONG.optionalFieldOf("rpg_total_experience", 0L).forGetter(PlayerRpgData::totalExperience),
            Codec.LONG.optionalFieldOf("strength", 0L).forGetter(PlayerRpgData::strength),
            Codec.LONG.optionalFieldOf("agility", 0L).forGetter(PlayerRpgData::agility),
            Codec.LONG.optionalFieldOf("constitution", 0L).forGetter(PlayerRpgData::constitution),
            Codec.LONG.optionalFieldOf("intelligence", 0L).forGetter(PlayerRpgData::intelligence),
            Codec.LONG.optionalFieldOf("spirit", 0L).forGetter(PlayerRpgData::spirit),
            Codec.BOOL.optionalFieldOf("progression_initialized", false).forGetter(PlayerRpgData::progressionInitialized),
            Codec.INT.optionalFieldOf("diagnostic_counter", 0).forGetter(PlayerRpgData::diagnosticCounter)
    ).apply(instance, PlayerRpgData::new));

    public static final StreamCodec<ByteBuf, PlayerRpgData> STREAM_CODEC = StreamCodec.of(
            PlayerRpgData::writeToBuffer, PlayerRpgData::readFromBuffer
    );

    public PlayerRpgData {
        rpgLevel = Math.max(0, Math.min(RpgProgression.MAX_LEVEL, rpgLevel));
        experienceProgress = Math.max(0L, experienceProgress);
        totalExperience = Math.max(0L, totalExperience);
        strength = nonNegative(strength);
        agility = nonNegative(agility);
        constitution = nonNegative(constitution);
        intelligence = nonNegative(intelligence);
        spirit = nonNegative(spirit);
        diagnosticCounter = Math.max(0, diagnosticCounter);
    }

    public PlayerRpgData initializeProgression(int startingLevel) {
        int safeLevel = Math.max(0, Math.min(RpgProgression.MAX_LEVEL, startingLevel));
        return new PlayerRpgData(safeLevel, 0L, 0L, strength, agility, constitution,
                intelligence, spirit, true, diagnosticCounter);
    }

    public PlayerRpgData withProgression(int level, long progress, long totalXp) {
        return new PlayerRpgData(level, progress, totalXp, strength, agility, constitution,
                intelligence, spirit, true, diagnosticCounter);
    }

    public long allocatedAttributePoints() {
        long total = 0L;
        total = saturatedAdd(total, strength);
        total = saturatedAdd(total, agility);
        total = saturatedAdd(total, constitution);
        total = saturatedAdd(total, intelligence);
        return saturatedAdd(total, spirit);
    }

    public long totalAttributePoints() {
        return RpgProgression.totalAttributePointsAtLevel(rpgLevel);
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

    public PlayerRpgData withDiagnosticCounter(int value) {
        return new PlayerRpgData(rpgLevel, experienceProgress, totalExperience, strength,
                agility, constitution, intelligence, spirit, progressionInitialized, value);
    }

    private PlayerRpgData withAttributes(long s, long a, long c, long i, long sp) {
        return new PlayerRpgData(rpgLevel, experienceProgress, totalExperience, s, a, c,
                i, sp, progressionInitialized, diagnosticCounter);
    }

    private static long nonNegative(long value) { return Math.max(0L, value); }

    private static long saturatedAdd(long a, long b) {
        return b > 0L && a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    private static void writeToBuffer(ByteBuf b, PlayerRpgData d) {
        b.writeInt(d.rpgLevel);
        b.writeLong(d.experienceProgress);
        b.writeLong(d.totalExperience);
        b.writeLong(d.strength);
        b.writeLong(d.agility);
        b.writeLong(d.constitution);
        b.writeLong(d.intelligence);
        b.writeLong(d.spirit);
        b.writeBoolean(d.progressionInitialized);
        b.writeInt(d.diagnosticCounter);
    }

    private static PlayerRpgData readFromBuffer(ByteBuf b) {
        return new PlayerRpgData(b.readInt(), b.readLong(), b.readLong(), b.readLong(),
                b.readLong(), b.readLong(), b.readLong(), b.readLong(), b.readBoolean(), b.readInt());
    }
}
