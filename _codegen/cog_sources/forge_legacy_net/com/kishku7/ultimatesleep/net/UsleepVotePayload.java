package com.kishku7.ultimatesleep.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> server: cast a sleep vote (Yes/No).
 * Forge legacy-net twin: plain SimpleChannel message record (NOT a CustomPacketPayload).
 * Same ctor + accessor shapes as the modern record so shared GUI code compiles unchanged.
 */
public record UsleepVotePayload(boolean yes) {

    public static void write(UsleepVotePayload msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.yes());
    }

    public static UsleepVotePayload decode(FriendlyByteBuf buf) {
        return new UsleepVotePayload(buf.readBoolean());
    }
}
