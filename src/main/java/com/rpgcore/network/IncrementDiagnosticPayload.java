package com.rpgcore.network;

import com.rpgcore.RpgCoreMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record IncrementDiagnosticPayload() implements CustomPacketPayload {
    public static final Type<IncrementDiagnosticPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RpgCoreMod.MOD_ID, "increment_diagnostic")
    );

    public static final StreamCodec<ByteBuf, IncrementDiagnosticPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> { },
            buffer -> new IncrementDiagnosticPayload()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
