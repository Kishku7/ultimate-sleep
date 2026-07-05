package com.kishku7.ultimatesleep.net;

import java.util.function.Consumer;

/**
 * Loader-neutral C2S sender (legacy-net twin). On 1.20-1.20.4 there is no CustomPacketPayload,
 * so the payload holders are plain records and SENDER is typed Object: each loader's client init
 * installs a dispatcher that maps the holder to its channel id + FriendlyByteBuf. Shared client
 * code calls sendToServer(new UsleepSetPayload(...)) exactly as it does on modern eras.
 */
public final class ClientNet {
    private ClientNet() {}
    public static volatile Consumer<Object> SENDER = p -> {};
    public static void sendToServer(Object payload) { SENDER.accept(payload); }
}
