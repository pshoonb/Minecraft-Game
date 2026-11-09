package com.rpgcore.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Persistent, per-player RPG data.
 *
 * The first prototype intentionally stores the future RPG fields without
 * applying gameplay effects yet. All values are server-owned.
 */
public record PlayerRpgData(
        int health,
        int defense,
        int attack,
        int speed,
        int jumpHeight,
        int luck,
        int miningSpeed,
        int lifetimeHighestLevel,
        int lifetimeEarnedPoints,
        int availablePoints,
        int rewardBaselineLevel,
        int diagnosticCounter
) {
    public static final PlayerRpgData EMPTY = new PlayerRpgData(
            0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0
    );

    public static final Codec<PlayerRpgData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("health", 0).forGetter(PlayerRpgData::health),
            Codec.INT.optionalFieldOf("defense", 0).forGetter(PlayerRpgData::defense),
            Codec.INT.optionalFieldOf("attack", 0).forGetter(PlayerRpgData::attack),
            Codec.INT.optionalFieldOf("speed", 0).forGetter(PlayerRpgData::speed),
            Codec.INT.optionalFieldOf("jump_height", 0).forGetter(PlayerRpgData::jumpHeight),
            Codec.INT.optionalFieldOf("luck", 0).forGetter(PlayerRpgData::luck),
            Codec.INT.optionalFieldOf("mining_speed", 0).forGetter(PlayerRpgData::miningSpeed),
            Codec.INT.optionalFieldOf("lifetime_highest_level", 0).forGetter(PlayerRpgData::lifetimeHighestLevel),
            Codec.INT.optionalFieldOf("lifetime_earned_points", 0).forGetter(PlayerRpgData::lifetimeEarnedPoints),
            Codec.INT.optionalFieldOf("available_points", 0).forGetter(PlayerRpgData::availablePoints),
            Codec.INT.optionalFieldOf("reward_baseline_level", 0).forGetter(PlayerRpgData::rewardBaselineLevel),
            Codec.INT.optionalFieldOf("diagnostic_counter", 0).forGetter(PlayerRpgData::diagnosticCounter)
    ).apply(instance, PlayerRpgData::new));

    public static final StreamCodec<ByteBuf, PlayerRpgData> STREAM_CODEC = StreamCodec.of(
            PlayerRpgData::writeToBuffer,
            PlayerRpgData::readFromBuffer
    );

    public PlayerRpgData {
        health = nonNegative(health);
        defense = nonNegative(defense);
        attack = nonNegative(attack);
        speed = nonNegative(speed);
        jumpHeight = nonNegative(jumpHeight);
        luck = nonNegative(luck);
        miningSpeed = nonNegative(miningSpeed);
        lifetimeHighestLevel = nonNegative(lifetimeHighestLevel);
        lifetimeEarnedPoints = nonNegative(lifetimeEarnedPoints);
        availablePoints = nonNegative(availablePoints);
        rewardBaselineLevel = nonNegative(rewardBaselineLevel);
        diagnosticCounter = nonNegative(diagnosticCounter);
    }

    private static int nonNegative(int value) {
        return Math.max(0, value);
    }

    public PlayerRpgData withDiagnosticCounter(int value) {
        return new PlayerRpgData(
                health, defense, attack, speed, jumpHeight, luck, miningSpeed,
                lifetimeHighestLevel, lifetimeEarnedPoints, availablePoints,
                rewardBaselineLevel, value
        );
    }

    private static void writeToBuffer(ByteBuf buffer, PlayerRpgData data) {
        buffer.writeInt(data.health);
        buffer.writeInt(data.defense);
        buffer.writeInt(data.attack);
        buffer.writeInt(data.speed);
        buffer.writeInt(data.jumpHeight);
        buffer.writeInt(data.luck);
        buffer.writeInt(data.miningSpeed);
        buffer.writeInt(data.lifetimeHighestLevel);
        buffer.writeInt(data.lifetimeEarnedPoints);
        buffer.writeInt(data.availablePoints);
        buffer.writeInt(data.rewardBaselineLevel);
        buffer.writeInt(data.diagnosticCounter);
    }

    private static PlayerRpgData readFromBuffer(ByteBuf buffer) {
        return new PlayerRpgData(
                buffer.readInt(), buffer.readInt(), buffer.readInt(),
                buffer.readInt(), buffer.readInt(), buffer.readInt(),
                buffer.readInt(), buffer.readInt(), buffer.readInt(),
                buffer.readInt(), buffer.readInt(), buffer.readInt()
        );
    }
}
