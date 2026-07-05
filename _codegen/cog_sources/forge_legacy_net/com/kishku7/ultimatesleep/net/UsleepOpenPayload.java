package com.kishku7.ultimatesleep.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server -> client: open the admin panel (sent in response to /usleep gui for modded clients).
 * Forge legacy-net twin: plain SimpleChannel message record (NOT a CustomPacketPayload).
 */
public record UsleepOpenPayload() {

    public static void write(UsleepOpenPayload msg, FriendlyByteBuf buf) {}

    public static UsleepOpenPayload decode(FriendlyByteBuf buf) {
        return new UsleepOpenPayload();
    }
}
