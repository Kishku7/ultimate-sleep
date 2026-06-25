package com.kishku7.ultimatesleep.client;

import com.kishku7.ultimatesleep.net.UsleepOpenPayload;
import com.kishku7.ultimatesleep.net.UsleepSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * Client entrypoint. Server-driven hooks:
 *   - SYNC: refresh the settings cache (and any open admin screen).
 *   - OPEN: open the admin panel (sent for /usleep gui to modded clients).
 *
 * The sleep-vote prompt is intentionally NOT a screen -- it is shown server-side on the action bar
 * so it never grabs control mid-game, and works for vanilla clients too. The GUI here is optional
 * polish; everything is reachable via /usleep commands.
 */
public final class UltimateSleepClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        com.kishku7.ultimatesleep.net.ClientNet.SENDER = ClientPlayNetworking::send;
        ClientPlayNetworking.registerGlobalReceiver(UsleepSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    ClientState.update(payload.json());
                    if (ScreenCompat.currentScreen() instanceof UltimateSleepScreen s) {
                        s.refresh();
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(UsleepOpenPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ScreenCompat.setScreen(new UltimateSleepScreen())));
    }
}
