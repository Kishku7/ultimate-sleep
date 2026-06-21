package com.kishku7.ultimatesleep.client;

import com.kishku7.ultimatesleep.net.UsleepOpenPayload;
import com.kishku7.ultimatesleep.net.UsleepSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * Client entrypoint for the admin GUI. Two server-driven hooks:
 *   - SYNC payload: refresh the {@link ClientState} cache (and any open screen).
 *   - OPEN payload: open the panel (the server sends this in response to /usleep gui, only to
 *     clients that have this mod -- vanilla clients get a chat hint instead).
 *
 * The GUI is optional client polish; everything is reachable via the /usleep commands.
 */
public final class UltimateSleepClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(UsleepSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    ClientState.update(payload.json());
                    if (Minecraft.getInstance().screen instanceof UltimateSleepScreen s) {
                        s.refresh();
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(UsleepOpenPayload.TYPE, (payload, context) ->
                context.client().execute(() -> Minecraft.getInstance().setScreen(new UltimateSleepScreen())));
    }
}
