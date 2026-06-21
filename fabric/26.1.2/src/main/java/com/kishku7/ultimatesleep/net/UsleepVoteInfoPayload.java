package com.kishku7.ultimatesleep.net;

import com.kishku7.ultimatesleep.UltimateSleep;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> client: live vote tally + who's currently in bed, pushed about once a second while a
 * vote is active (only when show_sleepers_on_vote_screen is on). The popup renders it as one line.
 */
public record UsleepVoteInfoPayload(int yes, int no, String sleepers) implements CustomPacketPayload {

    public static final Type<UsleepVoteInfoPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(UltimateSleep.MOD_ID, "vote_info"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UsleepVoteInfoPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UsleepVoteInfoPayload::yes,
            ByteBufCodecs.VAR_INT, UsleepVoteInfoPayload::no,
            ByteBufCodecs.STRING_UTF8, UsleepVoteInfoPayload::sleepers,
            UsleepVoteInfoPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
