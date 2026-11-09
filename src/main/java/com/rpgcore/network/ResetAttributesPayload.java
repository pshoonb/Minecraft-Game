package com.rpgcore.network;

import com.rpgcore.RpgCoreMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ResetAttributesPayload() implements CustomPacketPayload {
    public static final Type<ResetAttributesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RpgCoreMod.MOD_ID, "reset_attributes"));
    public static final StreamCodec<ByteBuf, ResetAttributesPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {}, buffer -> new ResetAttributesPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
