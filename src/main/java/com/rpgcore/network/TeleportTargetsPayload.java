package com.rpgcore.network;
import com.rpgcore.RpgCoreMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
public record TeleportTargetsPayload(List<TeleportTargetsPayload.Target> targets) implements CustomPacketPayload {
    private static final int MAX_TARGETS = 1000;
    private static final int MAX_NAME_BYTES = 64;
    public static final Type<TeleportTargetsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RpgCoreMod.MOD_ID, "teleport_targets"));
    public static final StreamCodec<ByteBuf, TeleportTargetsPayload> STREAM_CODEC = StreamCodec.of(TeleportTargetsPayload::encode, TeleportTargetsPayload::decode);
    public TeleportTargetsPayload { targets = List.copyOf(targets); }
    private static void encode(ByteBuf b, TeleportTargetsPayload p) {
        int size = Math.min(p.targets().size(), MAX_TARGETS);
        b.writeInt(size);
        for (int i = 0; i < size; i++) {
            Target t = p.targets().get(i);
            b.writeLong(t.playerId().getMostSignificantBits());
            b.writeLong(t.playerId().getLeastSignificantBits());
            byte[] name = t.playerName().getBytes(StandardCharsets.UTF_8);
            int length = Math.min(name.length, MAX_NAME_BYTES);
            b.writeInt(length);
            b.writeBytes(name, 0, length);
        }
    }
    private static TeleportTargetsPayload decode(ByteBuf b) {
        int size = Math.max(0, Math.min(b.readInt(), MAX_TARGETS));
        List<Target> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            UUID id = new UUID(b.readLong(), b.readLong());
            int length = Math.max(0, Math.min(b.readInt(), MAX_NAME_BYTES));
            byte[] name = new byte[length];
            b.readBytes(name);
            result.add(new Target(id, new String(name, StandardCharsets.UTF_8)));
        }
        return new TeleportTargetsPayload(result);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public record Target(UUID playerId, String playerName) {
        public Target { if (playerName == null) playerName = ""; }
    }
}
