package com.kishku7.ultimatesleep.client;

import com.kishku7.ultimatesleep.net.ClientNet;
//[[[cog
//import sys; sys.path.insert(0, codegen); import compat_loaders
//cog.outl(compat_loaders.client_send_import(ver))
//]]]
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
//[[[end]]]

/**
 * Client-only NeoForge wiring. Kept minimal: hands the client->server sender to the shared
 * ClientNet seam. Payload receivers are registered centrally in UltimateSleepNet (handlers
 * delegate to NeoClientHooks lazily). Era drift handled by cog: PacketDistributor.sendToServer
 * below 1.21.8 vs ClientPacketDistributor.sendToServer from 1.21.8 (sendToServer is gone from
 * PacketDistributor at neoforge 21.8+).
 */
public final class UltimateSleepNeoForgeClient {

    private UltimateSleepNeoForgeClient() {}

    public static void init() {
        //[[[cog
        //import sys; sys.path.insert(0, codegen); import compat_loaders
        //cog.outl("        ClientNet.SENDER = %s;" % compat_loaders.client_sender_ref(ver))
        //]]]
        ClientNet.SENDER = ClientPacketDistributor::sendToServer;
        //[[[end]]]
    }
}
