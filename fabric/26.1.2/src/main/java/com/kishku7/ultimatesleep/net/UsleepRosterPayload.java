package com.kishku7.ultimatesleep.net;

import com.kishku7.ultimatesleep.UltimateSleep;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: manage the sleep-admin roster. action = "add" | "remove". */
public record UsleepRosterPayload(String action, String name) implements CustomPacketPayload {

    public static final Type<UsleepRosterPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(UltimateSleep.MOD_ID, "roster"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UsleepRosterPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, UsleepRosterPayload::action,
            ByteBufCodecs.STRING_UTF8, UsleepRosterPayload::name,
            UsleepRosterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
