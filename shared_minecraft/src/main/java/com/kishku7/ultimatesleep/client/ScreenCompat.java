package com.kishku7.ultimatesleep.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Bridges the 26.1 -> 26.2 screen API change so one client source serves the whole 26.x line.
 * 26.1: Minecraft.setScreen(Screen) + Minecraft.screen (field).
 * 26.2+: Minecraft.gui.setScreen(Screen) + Minecraft.gui.screen() (the field/method moved onto Gui).
 */
public final class ScreenCompat {
    private ScreenCompat() {}

    public static void setScreen(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        try {
            Field guiF = Minecraft.class.getField("gui");
            Object gui = guiF.get(mc);
            for (Method m : gui.getClass().getMethods()) {
                if (m.getName().equals("setScreen") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isAssignableFrom(Screen.class)) {
                    m.invoke(gui, screen);
                    return;
                }
            }
        } catch (NoSuchFieldException ignored) {
        } catch (Exception e) {
            throw new RuntimeException("US setScreen via gui failed", e);
        }
        try {
            Minecraft.class.getMethod("setScreen", Screen.class).invoke(mc, screen);
        } catch (Exception e) {
            throw new RuntimeException("US setScreen failed", e);
        }
    }

    public static Screen currentScreen() {
        Minecraft mc = Minecraft.getInstance();
        try {
            Field guiF = Minecraft.class.getField("gui");
            Object gui = guiF.get(mc);
            Method m = gui.getClass().getMethod("screen");
            return (Screen) m.invoke(gui);
        } catch (Exception ignored) {
        }
        try {
            Field f = Minecraft.class.getField("screen");
            return (Screen) f.get(mc);
        } catch (Exception e) {
            return null;
        }
    }
}
