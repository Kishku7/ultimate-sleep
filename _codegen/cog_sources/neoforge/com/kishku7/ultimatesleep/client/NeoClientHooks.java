package com.kishku7.ultimatesleep.client;

/**
 * Client-side payload handling (NeoForge). Only ever invoked on the client -- referenced from
 * UltimateSleepNet's clientbound handlers inside lambdas so this class is not linked during
 * dedicated-server payload registration. No cog blocks: ClientState/ScreenCompat/
 * UltimateSleepScreen are era-flavoured upstream by cog-gen (shared vs shared_pre26), and this
 * call surface is identical in both eras.
 */
public final class NeoClientHooks {

    private NeoClientHooks() {}

    public static void onSync(String json) {
        ClientState.update(json);
        if (ScreenCompat.currentScreen() instanceof UltimateSleepScreen s) {
            s.refresh();
        }
    }

    public static void onOpen() {
        ScreenCompat.setScreen(new UltimateSleepScreen());
    }
}
