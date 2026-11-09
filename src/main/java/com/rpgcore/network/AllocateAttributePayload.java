package com.rpgcore.network;

import com.rpgcore.RpgCoreMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AllocateAttributePayload(int attributeId, int amount) implements CustomPacketPayload {
    public static final Type<AllocateAttributePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RpgCoreMod.MOD_ID, "allocate_attribute"));

    public static final StreamCodec<ByteBuf, AllocateAttributePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeByte(payload.attributeId);
                buffer.writeInt(payload.amount);
            },
            buffer -> new AllocateAttributePayload(buffer.readUnsignedByte(), buffer.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
