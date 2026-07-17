package com.kishku7.ultimatesleep.net;

import com.kishku7.ultimatesleep.compat.Era;

import com.kishku7.ultimatesleep.UltimateSleep;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: a sleep vote started; show the bottom prompt for {@code seconds}. */
public record UsleepVoteStartPayload(String question, int seconds) implements CustomPacketPayload {

    public static final Type<UsleepVoteStartPayload> TYPE =
            new Type<>(Era.id("vote_start"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UsleepVoteStartPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, UsleepVoteStartPayload::question,
            ByteBufCodecs.VAR_INT, UsleepVoteStartPayload::seconds,
            UsleepVoteStartPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
