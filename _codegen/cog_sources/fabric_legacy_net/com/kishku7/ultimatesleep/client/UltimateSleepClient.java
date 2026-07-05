package com.kishku7.ultimatesleep.client;

import com.kishku7.ultimatesleep.net.ClientNet;
import com.kishku7.ultimatesleep.net.UsleepPayloads;
import com.kishku7.ultimatesleep.net.UsleepRosterPayload;
import com.kishku7.ultimatesleep.net.UsleepSetPayload;
import com.kishku7.ultimatesleep.net.UsleepVotePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client entrypoint -- LEGACY-NET twin (1.20-1.20.4). Server-driven hooks:
 *   - SYNC: refresh the settings cache (and any open admin screen).
 *   - OPEN: open the admin panel (sent for /usleep gui to modded clients).
 *
 * The sleep-vote prompt is intentionally NOT a screen -- it is shown server-side on the action bar
 * so it never grabs control mid-game, and works for vanilla clients too. The GUI here is optional
 * polish; everything is reachable via /usleep commands. Old fabric-networking-api-v1: raw
 * ResourceLocation channels + FriendlyByteBuf; ClientNet.SENDER dispatches the plain payload
 * holders by instanceof onto their channels.
 */
public final class UltimateSleepClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientNet.SENDER = UltimateSleepClient::dispatch;

        ClientPlayNetworking.registerGlobalReceiver(UsleepPayloads.SYNC,
                (client, handler, buf, responseSender) -> {
                    String json = UsleepPayloads.decodeSync(buf);
                    client.execute(() -> {
                        ClientState.update(json);
                        if (ScreenCompat.currentScreen() instanceof UltimateSleepScreen s) {
                            s.refresh();
                        }
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(UsleepPayloads.OPEN,
                (client, handler, buf, responseSender) ->
                        client.execute(() -> ScreenCompat.setScreen(new UltimateSleepScreen())));
    }

    /** C2S dispatch: map a plain payload holder onto its legacy channel. */
    private static void dispatch(Object payload) {
        if (payload instanceof UsleepSetPayload p) {
            ClientPlayNetworking.send(UsleepPayloads.SET, UsleepPayloads.encodeSet(p));
        } else if (payload instanceof UsleepRosterPayload p) {
            ClientPlayNetworking.send(UsleepPayloads.ROSTER, UsleepPayloads.encodeRoster(p));
        } else if (payload instanceof UsleepVotePayload p) {
            ClientPlayNetworking.send(UsleepPayloads.VOTE, UsleepPayloads.encodeVote(p));
        } else {
            throw new IllegalArgumentException(
                    "Unknown C2S payload type: " + payload.getClass().getName());
        }
    }
}
