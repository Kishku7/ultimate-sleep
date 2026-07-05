package com.kishku7.ultimatesleep.client;

import com.kishku7.ultimatesleep.net.ClientNet;
import com.kishku7.ultimatesleep.net.UltimateSleepNet;

/**
 * Client-only Forge wiring: hands the client->server sender to the shared ClientNet seam and
 * points the S2C sinks at the client screen cache. Only ever called on the client dist, so the
 * GUI classes referenced by the sink lambdas are never linked on a dedicated server.
 *
 * No cog blocks: this exact text compiles on BOTH net eras -- the modern twin's ClientNet takes
 * Consumer&lt;CustomPacketPayload&gt; and sendToServer(CustomPacketPayload); the legacy
 * (1.20.1) twins take Consumer&lt;Object&gt; and sendToServer(Object). The method reference and
 * the sink shapes are source-compatible with either.
 */
public final class UltimateSleepForgeClient {

    private UltimateSleepForgeClient() {}

    public static void init() {
        ClientNet.SENDER = UltimateSleepNet::sendToServer;

        UltimateSleepNet.syncSink = json -> {
            ClientState.update(json);
            if (ScreenCompat.currentScreen() instanceof UltimateSleepScreen s) {
                s.refresh();
            }
        };
        UltimateSleepNet.openSink = () -> ScreenCompat.setScreen(new UltimateSleepScreen());
    }
}
