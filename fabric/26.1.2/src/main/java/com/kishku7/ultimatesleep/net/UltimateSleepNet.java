package com.kishku7.ultimatesleep.net;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kishku7.ultimatesleep.UltimateSleep;
import com.kishku7.ultimatesleep.config.Settings;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side back-channel for the (optional) client admin GUI. The server stays fully
 * command-driven for vanilla clients; modded clients additionally use these payloads to read and
 * change settings without parsing chat. All mutations are permission-checked server-side, and a
 * fresh snapshot is broadcast to every modded client afterward so open panels stay live.
 */
public final class UltimateSleepNet {

    private UltimateSleepNet() {}

    private static final Gson GSON = new Gson();

    /** Register payload types (runs on both client and server). */
    public static void registerTypes() {
        PayloadTypeRegistry.serverboundPlay().register(UsleepRequestPayload.TYPE, UsleepRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UsleepSetPayload.TYPE, UsleepSetPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UsleepRosterPayload.TYPE, UsleepRosterPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(UsleepSyncPayload.TYPE, UsleepSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(UsleepOpenPayload.TYPE, UsleepOpenPayload.CODEC);
    }

    /** Register server-side receivers. */
    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(UsleepRequestPayload.TYPE, (payload, context) ->
                sendSyncTo(context.player()));

        ServerPlayNetworking.registerGlobalReceiver(UsleepSetPayload.TYPE, (payload, context) -> {
            ServerPlayer p = context.player();
            if (UltimateSleep.permissions().canSet(p.createCommandSourceStack())) {
                String err = UltimateSleep.settings().setRaw(payload.key(), payload.value());
                if (err == null) {
                    UltimateSleep.settings().save();
                    MinecraftServer s = p.level().getServer();
                    if (s != null) UltimateSleep.engine().applyConfig(s);
                }
            }
            broadcast(p);
        });

        ServerPlayNetworking.registerGlobalReceiver(UsleepRosterPayload.TYPE, (payload, context) -> {
            ServerPlayer p = context.player();
            if (UltimateSleep.permissions().canAdmin(p.createCommandSourceStack())) {
                if ("add".equalsIgnoreCase(payload.action())) {
                    UltimateSleep.permissions().addAdmin(payload.name());
                } else if ("remove".equalsIgnoreCase(payload.action())) {
                    UltimateSleep.permissions().removeAdmin(payload.name());
                }
            }
            broadcast(p);
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
        if (!ServerPlayNetworking.canSend(p, UsleepOpenPayload.TYPE)) return false;
        sendSyncTo(p);
        ServerPlayNetworking.send(p, new UsleepOpenPayload());
        return true;
    }

    public static void sendSyncTo(ServerPlayer p) {
        if (!ServerPlayNetworking.canSend(p, UsleepSyncPayload.TYPE)) return;
        ServerPlayNetworking.send(p, new UsleepSyncPayload(buildJson(p)));
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
