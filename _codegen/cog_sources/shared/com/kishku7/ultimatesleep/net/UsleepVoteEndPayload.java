package com.kishku7.ultimatesleep.net;

import com.kishku7.ultimatesleep.compat.Era;

import com.kishku7.ultimatesleep.UltimateSleep;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the sleep vote ended; close the bottom prompt. */
public record UsleepVoteEndPayload() implements CustomPacketPayload {

    public static final Type<UsleepVoteEndPayload> TYPE =
            new Type<>(Era.id("vote_end"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UsleepVoteEndPayload> CODEC =
            StreamCodec.unit(new UsleepVoteEndPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
