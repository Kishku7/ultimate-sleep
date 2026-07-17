package com.kishku7.ultimatesleep.net;

import com.kishku7.ultimatesleep.compat.Era;

import com.kishku7.ultimatesleep.UltimateSleep;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: open the admin panel (sent in response to /usleep gui for modded clients). */
public record UsleepOpenPayload() implements CustomPacketPayload {

    public static final Type<UsleepOpenPayload> TYPE =
            new Type<>(Era.id("open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UsleepOpenPayload> CODEC =
            StreamCodec.unit(new UsleepOpenPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
