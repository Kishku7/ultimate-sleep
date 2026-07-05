package com.kishku7.ultimatesleep.net;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kishku7.ultimatesleep.UltimateSleep;
import com.kishku7.ultimatesleep.config.Settings;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side back-channel for the (optional) client admin GUI -- LEGACY-NET twin (1.20-1.20.4,
 * fabric-networking-api-v1 1.3.x): ResourceLocation channels + FriendlyByteBuf instead of typed
 * CustomPacketPayload records. Same public API as the modern fabric UltimateSleepNet so shared
 * command/GUI code compiles unchanged. Receivers read the buf on the netty thread, then hop to
 * the server thread via server.execute() before touching game state. The vestigial
 * vote_start/vote_end/vote_info channels are dropped from the wire protocol on this era; their
 * senders remain as no-op stubs for API parity.
 */
public final class UltimateSleepNet {

    private UltimateSleepNet() {}

    private static final Gson GSON = new Gson();

    /** Legacy era: there is no payload-type registry -- channels are implicit. API-parity no-op. */
    public static void registerTypes() {}

    /** Register server-side receivers. */
    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(UsleepPayloads.REQUEST,
                (server, player, handler, buf, responseSender) ->
                        server.execute(() -> sendSyncTo(player)));

        ServerPlayNetworking.registerGlobalReceiver(UsleepPayloads.VOTE,
                (server, player, handler, buf, responseSender) -> {
                    boolean yes = UsleepPayloads.decodeVote(buf).yes();
                    server.execute(() -> UltimateSleep.vote().castVote(player, yes));
                });

        ServerPlayNetworking.registerGlobalReceiver(UsleepPayloads.SET,
                (server, player, handler, buf, responseSender) -> {
                    UsleepSetPayload payload = UsleepPayloads.decodeSet(buf);
                    server.execute(() -> {
                        if (UltimateSleep.permissions().canSet(player.createCommandSourceStack())) {
                            String err = UltimateSleep.settings().setRaw(payload.key(), payload.value());
                            if (err == null) {
                                UltimateSleep.settings().save();
                                UltimateSleep.engine().applyConfig(server);
                            }
                        }
                        broadcast(player);
                    });
                });

        ServerPlayNetworking.registerGlobalReceiver(UsleepPayloads.ROSTER,
                (server, player, handler, buf, responseSender) -> {
                    UsleepRosterPayload payload = UsleepPayloads.decodeRoster(buf);
                    server.execute(() -> {
                        if (UltimateSleep.permissions().canAdmin(player.createCommandSourceStack())) {
                            if ("add".equalsIgnoreCase(payload.action())) {
                                UltimateSleep.permissions().addAdmin(payload.name());
                            } else if ("remove".equalsIgnoreCase(payload.action())) {
                                UltimateSleep.permissions().removeAdmin(payload.name());
                            }
                        }
                        broadcast(player);
                    });
                });
    }

    private static void broadcast(ServerPlayer origin) {
        MinecraftServer server = origin.level().getServer();
        if (server == null) {
            sendSyncTo(origin);
            return;
        }
        for (ServerPlayer pl : server.getPlayerList().getPlayers()) {
            sendSyncTo(pl);
        }
    }

    public static boolean openGuiFor(ServerPlayer p) {
        if (!ServerPlayNetworking.canSend(p, UsleepPayloads.OPEN)) return false;
        sendSyncTo(p);
        ServerPlayNetworking.send(p, UsleepPayloads.OPEN, UsleepPayloads.empty());
        return true;
    }

    /** No-op on this era: the vote_start channel is dropped (no client listens). API parity only. */
    public static void sendVoteStart(ServerPlayer p, String question, int seconds) {}

    /** No-op on this era: the vote_end channel is dropped. API parity only. */
    public static void sendVoteEnd(ServerPlayer p) {}

    /** No-op on this era: the vote_info channel is dropped. API parity only. */
    public static void sendVoteInfo(ServerPlayer p, int yes, int no, String sleepers) {}

    public static void sendSyncTo(ServerPlayer p) {
        if (!ServerPlayNetworking.canSend(p, UsleepPayloads.SYNC)) return;
        ServerPlayNetworking.send(p, UsleepPayloads.SYNC, UsleepPayloads.encodeSync(buildJson(p)));
    }

    private static String buildJson(ServerPlayer p) {
        JsonObject root = new JsonObject();
        JsonObject settings = new JsonObject();
        JsonObject types = new JsonObject();
        JsonObject allowed = new JsonObject();
        for (Settings.Entry e : UltimateSleep.settings().all().values()) {
            settings.addProperty(e.key, e.asString());
            types.addProperty(e.key, e.type.name());
            if (e.type == Settings.Type.ENUM) {
                JsonArray arr = new JsonArray();
                for (String a : e.allowed) arr.add(a);
                allowed.add(e.key, arr);
            }
        }
        root.add("settings", settings);
        root.add("types", types);
        root.add("allowed", allowed);

        JsonArray admins = new JsonArray();
        for (String a : UltimateSleep.permissions().list()) admins.add(a);
        root.add("admins", admins);

        root.addProperty("afkOwner", UltimateSleep.afkCommands().ownerLabel());
        var src = p.createCommandSourceStack();
        root.addProperty("canSet", UltimateSleep.permissions().canSet(src));
        root.addProperty("canAdmin", UltimateSleep.permissions().canAdmin(src));
        return GSON.toJson(root);
    }
}
