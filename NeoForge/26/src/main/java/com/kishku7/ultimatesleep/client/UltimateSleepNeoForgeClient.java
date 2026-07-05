package com.kishku7.ultimatesleep.client;

import com.kishku7.ultimatesleep.net.ClientNet;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Client-only NeoForge wiring. Kept minimal: hands the client->server sender to the shared
 * ClientNet seam. Payload receivers are registered centrally in UltimateSleepNet (handlers
 * delegate to NeoClientHooks lazily).
 */
public final class UltimateSleepNeoForgeClient {

    private UltimateSleepNeoForgeClient() {}

    public static void init() {
        ClientNet.SENDER = ClientPacketDistributor::sendToServer;
    }
}
