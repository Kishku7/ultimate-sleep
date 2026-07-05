package com.kishku7.ultimatesleep.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> server: set one setting (server validates permission, applies, re-syncs).
 * Forge legacy-net twin: plain SimpleChannel message record (NOT a CustomPacketPayload --
 * that interface does not exist on 1.20.1). Same ctor + accessor shapes as the modern record
 * so shared GUI code (UltimateSleepScreen) compiles unchanged.
 */
public record UsleepSetPayload(String key, String value) {

    public static void write(UsleepSetPayload msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.key());
        buf.writeUtf(msg.value());
    }

    public static UsleepSetPayload decode(FriendlyByteBuf buf) {
        return new UsleepSetPayload(buf.readUtf(), buf.readUtf());
    }
}
