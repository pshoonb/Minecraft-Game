package com.rpgcore.network;
import com.rpgcore.RpgCoreMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;
public record TeleportToPlayerPayload(UUID targetId) implements CustomPacketPayload {
    public static final Type<TeleportToPlayerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RpgCoreMod.MOD_ID, "teleport_to_player"));
    public static final StreamCodec<ByteBuf, TeleportToPlayerPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeLong(p.targetId().getMostSignificantBits()); b.writeLong(p.targetId().getLeastSignificantBits()); },
            b -> new TeleportToPlayerPayload(new UUID(b.readLong(), b.readLong())));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
