package com.kishku7.ultimatesleep.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> server: "send me the current settings snapshot." Empty body.
 * Forge legacy-net twin: plain SimpleChannel message record (NOT a CustomPacketPayload --
 * that interface does not exist on 1.20.1) with hand-rolled buf codecs.
 */
public record UsleepRequestPayload() {

    public static void write(UsleepRequestPayload msg, FriendlyByteBuf buf) {}

    public static UsleepRequestPayload decode(FriendlyByteBuf buf) {
        return new UsleepRequestPayload();
    }
}
