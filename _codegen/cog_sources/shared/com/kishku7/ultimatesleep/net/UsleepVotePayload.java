package com.kishku7.ultimatesleep.net;

import com.kishku7.ultimatesleep.compat.Era;

import com.kishku7.ultimatesleep.UltimateSleep;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: cast a sleep vote (Yes/No buttons on the popup). */
public record UsleepVotePayload(boolean yes) implements CustomPacketPayload {

    public static final Type<UsleepVotePayload> TYPE =
            new Type<>(Era.id("vote"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UsleepVotePayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, UsleepVotePayload::yes, UsleepVotePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
