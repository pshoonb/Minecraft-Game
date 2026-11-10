package com.rpgcore.network;
import com.rpgcore.RpgCoreMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record RequestTeleportTargetsPayload() implements CustomPacketPayload {
    public static final Type<RequestTeleportTargetsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RpgCoreMod.MOD_ID, "request_teleport_targets"));
    public static final StreamCodec<ByteBuf, RequestTeleportTargetsPayload> STREAM_CODEC = StreamCodec.of((b, p) -> { }, b -> new RequestTeleportTargetsPayload());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
