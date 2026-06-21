package com.kishku7.ultimatesleep.client;

import com.kishku7.ultimatesleep.net.UsleepOpenPayload;
import com.kishku7.ultimatesleep.net.UsleepSyncPayload;
import com.kishku7.ultimatesleep.net.UsleepVoteEndPayload;
import com.kishku7.ultimatesleep.net.UsleepVoteInfoPayload;
import com.kishku7.ultimatesleep.net.UsleepVoteStartPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * Client entrypoint. Server-driven hooks:
 *   - SYNC: refresh the settings cache (and any open admin screen).
 *   - OPEN: open the admin panel (sent for /usleep gui to modded clients).
 *   - VOTE_START / VOTE_END: show / hide the bottom-bar sleep-vote prompt.
 *   - VOTE_INFO: live tally + sleeper list on the open vote prompt.
 *
 * The GUI is optional client polish; everything is reachable via /usleep commands.
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

        ClientPlayNetworking.registerGlobalReceiver(UsleepVoteStartPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    VoteScreen.infoLine = "";
                    if (mc.screen == null) {
                        mc.setScreen(new VoteScreen(payload.question(), payload.seconds()));
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(UsleepVoteInfoPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    String line = "Yes " + payload.yes() + " / No " + payload.no();
                    if (payload.sleepers() != null && !payload.sleepers().isEmpty()) {
                        line += "  |  In bed: " + payload.sleepers();
                    }
                    VoteScreen.infoLine = line;
                }));

        ClientPlayNetworking.registerGlobalReceiver(UsleepVoteEndPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    VoteScreen.infoLine = "";
                    if (mc.screen instanceof VoteScreen) {
                        mc.setScreen(null);
                    }
                }));
    }
}
