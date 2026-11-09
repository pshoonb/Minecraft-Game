package com.rpgcore.network;

import com.rpgcore.RpgCoreMod;
import com.rpgcore.data.PlayerRpgData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record SyncRpgDataPayload(UUID playerId, PlayerRpgData data) implements CustomPacketPayload {
    public static final Type<SyncRpgDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RpgCoreMod.MOD_ID, "sync_rpg_data")
    );

    public static final StreamCodec<ByteBuf, SyncRpgDataPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeLong(payload.playerId.getMostSignificantBits());
                buffer.writeLong(payload.playerId.getLeastSignificantBits());
                PlayerRpgData.STREAM_CODEC.encode(buffer, payload.data);
            },
            buffer -> {
                UUID playerId = new UUID(buffer.readLong(), buffer.readLong());
                PlayerRpgData data = PlayerRpgData.STREAM_CODEC.decode(buffer);
                return new SyncRpgDataPayload(playerId, data);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
