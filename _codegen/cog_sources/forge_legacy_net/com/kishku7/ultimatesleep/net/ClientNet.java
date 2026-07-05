package com.kishku7.ultimatesleep.net;

import java.util.function.Consumer;

/**
 * Loader-neutral C2S sender (legacy-net twin, identical to the fabric legacy twin). On
 * 1.20-1.20.4 there is no CustomPacketPayload, so the payload holders are plain records and
 * SENDER is typed Object: the Forge client init points it at UltimateSleepNet.sendToServer
 * (SimpleChannel dispatches by message class -- no per-payload mapping needed). Shared client
 * code calls sendToServer(new UsleepSetPayload(...)) exactly as it does on modern eras.
 */
public final class ClientNet {
    private ClientNet() {}
    public static volatile Consumer<Object> SENDER = p -> {};
    public static void sendToServer(Object payload) { SENDER.accept(payload); }
}
