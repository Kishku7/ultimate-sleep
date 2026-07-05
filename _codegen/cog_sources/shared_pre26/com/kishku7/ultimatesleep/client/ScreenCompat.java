package com.kishku7.ultimatesleep.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Pre-26 twin of ScreenCompat: the 26.1/26.2 Gui split does not exist here, so both calls are
 * direct (no reflection -- mojmap-name reflection would MISS on intermediary/SRG runtimes anyway;
 * see mod-audit doctrine D1). Same API so client code is identical across eras.
 */
public final class ScreenCompat {
    private ScreenCompat() {}

    public static void setScreen(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    public static Screen currentScreen() {
        return Minecraft.getInstance().screen;
    }
}
