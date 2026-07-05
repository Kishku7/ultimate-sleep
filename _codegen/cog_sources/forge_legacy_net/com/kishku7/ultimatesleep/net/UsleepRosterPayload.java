package com.kishku7.ultimatesleep.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> server: manage the sleep-admin roster. action = "add" | "remove".
 * Forge legacy-net twin: plain SimpleChannel message record (NOT a CustomPacketPayload).
 * Same ctor + accessor shapes as the modern record so shared GUI code compiles unchanged.
 */
public record UsleepRosterPayload(String action, String name) {

    public static void write(UsleepRosterPayload msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.action());
        buf.writeUtf(msg.name());
    }

    public static UsleepRosterPayload decode(FriendlyByteBuf buf) {
        return new UsleepRosterPayload(buf.readUtf(), buf.readUtf());
    }
}
