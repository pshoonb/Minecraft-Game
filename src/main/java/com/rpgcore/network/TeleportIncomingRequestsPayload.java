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

/** Server snapshot of teleport requests addressed to the receiving player. */
public record TeleportIncomingRequestsPayload(List<Request> requests) implements CustomPacketPayload {
    private static final int MAX_REQUESTS = 1000;
    private static final int MAX_NAME_BYTES = 64;

    public static final Type<TeleportIncomingRequestsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpgCoreMod.MOD_ID, "teleport_incoming_requests"));
    public static final StreamCodec<ByteBuf, TeleportIncomingRequestsPayload> STREAM_CODEC =
            StreamCodec.of(TeleportIncomingRequestsPayload::encode, TeleportIncomingRequestsPayload::decode);

    public TeleportIncomingRequestsPayload {
        requests = List.copyOf(requests);
    }

    private static void encode(ByteBuf buffer, TeleportIncomingRequestsPayload payload) {
        int size = Math.min(payload.requests().size(), MAX_REQUESTS);
        buffer.writeInt(size);
        for (int i = 0; i < size; i++) {
            Request request = payload.requests().get(i);
            buffer.writeLong(request.requesterId().getMostSignificantBits());
            buffer.writeLong(request.requesterId().getLeastSignificantBits());
            byte[] name = request.playerName().getBytes(StandardCharsets.UTF_8);
            int length = Math.min(name.length, MAX_NAME_BYTES);
            buffer.writeInt(length);
            buffer.writeBytes(name, 0, length);
        }
    }

    private static TeleportIncomingRequestsPayload decode(ByteBuf buffer) {
        int size = Math.max(0, Math.min(buffer.readInt(), MAX_REQUESTS));
        List<Request> requests = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            UUID requesterId = new UUID(buffer.readLong(), buffer.readLong());
            int length = Math.max(0, Math.min(buffer.readInt(), MAX_NAME_BYTES));
            byte[] name = new byte[length];
            buffer.readBytes(name);
            requests.add(new Request(requesterId, new String(name, StandardCharsets.UTF_8)));
        }
        return new TeleportIncomingRequestsPayload(requests);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Request(UUID requesterId, String playerName) {
        public Request {
            if (playerName == null) {
                playerName = "";
            }
        }
    }
}
