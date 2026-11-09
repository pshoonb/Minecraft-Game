package com.rpgcore.network;

import com.rpgcore.RpgCoreMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestRpgDataPayload() implements CustomPacketPayload {
    public static final Type<RequestRpgDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RpgCoreMod.MOD_ID, "request_rpg_data")
    );

    public static final StreamCodec<ByteBuf, RequestRpgDataPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> { },
            buffer -> new RequestRpgDataPayload()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
