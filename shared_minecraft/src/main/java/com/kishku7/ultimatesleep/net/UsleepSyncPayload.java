package com.kishku7.ultimatesleep.net;

import com.kishku7.ultimatesleep.compat.Era;

import com.kishku7.ultimatesleep.UltimateSleep;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: full settings snapshot as a JSON string (parsed by the client GUI). */
public record UsleepSyncPayload(String json) implements CustomPacketPayload {

    public static final Type<UsleepSyncPayload> TYPE =
            new Type<>(Era.id("sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UsleepSyncPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, UsleepSyncPayload::json, UsleepSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
