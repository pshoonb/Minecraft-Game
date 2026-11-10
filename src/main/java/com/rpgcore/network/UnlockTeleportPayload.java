package com.rpgcore.network;
import com.rpgcore.RpgCoreMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record UnlockTeleportPayload() implements CustomPacketPayload {
    public static final Type<UnlockTeleportPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RpgCoreMod.MOD_ID, "unlock_teleport"));
    public static final StreamCodec<ByteBuf, UnlockTeleportPayload> STREAM_CODEC = StreamCodec.of((b, p) -> { }, b -> new UnlockTeleportPayload());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
