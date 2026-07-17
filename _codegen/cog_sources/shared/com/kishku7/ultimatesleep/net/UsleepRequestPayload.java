package com.kishku7.ultimatesleep.net;

import com.kishku7.ultimatesleep.compat.Era;

import com.kishku7.ultimatesleep.UltimateSleep;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: "send me the current settings snapshot." */
public record UsleepRequestPayload() implements CustomPacketPayload {

    public static final Type<UsleepRequestPayload> TYPE =
            new Type<>(Era.id("request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UsleepRequestPayload> CODEC =
            StreamCodec.unit(new UsleepRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
