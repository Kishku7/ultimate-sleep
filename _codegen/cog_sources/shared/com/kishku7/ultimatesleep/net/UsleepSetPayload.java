package com.kishku7.ultimatesleep.net;

import com.kishku7.ultimatesleep.compat.Era;

import com.kishku7.ultimatesleep.UltimateSleep;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: set one setting (server validates permission, applies, re-syncs). */
public record UsleepSetPayload(String key, String value) implements CustomPacketPayload {

    public static final Type<UsleepSetPayload> TYPE =
            new Type<>(Era.id("set"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UsleepSetPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, UsleepSetPayload::key,
            ByteBufCodecs.STRING_UTF8, UsleepSetPayload::value,
            UsleepSetPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
