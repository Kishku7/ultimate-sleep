package com.kishku7.ultimatesleep.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.function.Consumer;

/** Loader-neutral C2S sender. Each loader's client init sets SENDER; shared client code calls sendToServer(). */
public final class ClientNet {
    private ClientNet() {}
    public static volatile Consumer<CustomPacketPayload> SENDER = p -> {};
    public static void sendToServer(CustomPacketPayload payload) { SENDER.accept(payload); }
}
