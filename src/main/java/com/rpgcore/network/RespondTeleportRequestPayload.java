package com.rpgcore.network;

import com.rpgcore.RpgCoreMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Client response to one specific incoming teleport request. */
public record RespondTeleportRequestPayload(UUID requesterId, boolean accept) implements CustomPacketPayload {
    public static final Type<RespondTeleportRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpgCoreMod.MOD_ID, "respond_teleport_request"));
    public static final StreamCodec<ByteBuf, RespondTeleportRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeLong(payload.requesterId().getMostSignificantBits());
                        buffer.writeLong(payload.requesterId().getLeastSignificantBits());
                        buffer.writeBoolean(payload.accept());
                    },
                    buffer -> new RespondTeleportRequestPayload(
                            new UUID(buffer.readLong(), buffer.readLong()), buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
