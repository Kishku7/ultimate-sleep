package com.kishku7.ultimatesleep.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server -> client: full settings snapshot as a JSON string (parsed by the client GUI).
 * Forge legacy-net twin: plain SimpleChannel message record (NOT a CustomPacketPayload).
 */
public record UsleepSyncPayload(String json) {

    public static void write(UsleepSyncPayload msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.json());
    }

    public static UsleepSyncPayload decode(FriendlyByteBuf buf) {
        return new UsleepSyncPayload(buf.readUtf());
    }
}
