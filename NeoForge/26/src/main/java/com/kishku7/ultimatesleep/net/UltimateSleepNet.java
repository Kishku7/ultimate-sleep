package com.kishku7.ultimatesleep.net;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kishku7.ultimatesleep.UltimateSleep;
import com.kishku7.ultimatesleep.config.Settings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Server-side back-channel for the (optional) client admin GUI -- NeoForge wiring. Mirrors the
 * Fabric UltimateSleepNet 1:1 (same FQCN; per-loader seam class). Clientbound handlers delegate
 * to client.NeoClientHooks inside lambdas, so client classes are only linked when a payload
 * actually arrives on a client -- never during dedicated-server registration.
 */
public final class UltimateSleepNet {

    private UltimateSleepNet() {}

    private static final Gson GSON = new Gson();

    /** Mod-bus listener: registers all payloads + handlers (both directions). */
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(UltimateSleep.MOD_ID).versioned("1");

        registrar.playToServer(UsleepRequestPayload.TYPE, UsleepRequestPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer p) sendSyncTo(p);
        });

        registrar.playToServer(UsleepVotePayload.TYPE, UsleepVotePayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer p) UltimateSleep.vote().castVote(p, payload.yes());
        });

        registrar.playToServer(UsleepSetPayload.TYPE, UsleepSetPayload.CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer p)) return;
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

        registrar.playToServer(UsleepRosterPayload.TYPE, UsleepRosterPayload.CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer p)) return;
            if (UltimateSleep.permissions().canAdmin(p.createCommandSourceStack())) {
                if ("add".equalsIgnoreCase(payload.action())) {
                    UltimateSleep.permissions().addAdmin(payload.name());
                } else if ("remove".equalsIgnoreCase(payload.action())) {
                    UltimateSleep.permissions().removeAdmin(payload.name());
                }
            }
            broadcast(p);
        });

        registrar.playToClient(UsleepSyncPayload.TYPE, UsleepSyncPayload.CODEC, (payload, context) ->
                context.enqueueWork(() ->
                        com.kishku7.ultimatesleep.client.NeoClientHooks.onSync(payload.json())));

        registrar.playToClient(UsleepOpenPayload.TYPE, UsleepOpenPayload.CODEC, (payload, context) ->
                context.enqueueWork(() ->
                        com.kishku7.ultimatesleep.client.NeoClientHooks.onOpen()));

        registrar.playToClient(UsleepVoteStartPayload.TYPE, UsleepVoteStartPayload.CODEC, (payload, context) -> {});
        registrar.playToClient(UsleepVoteEndPayload.TYPE, UsleepVoteEndPayload.CODEC, (payload, context) -> {});
        registrar.playToClient(UsleepVoteInfoPayload.TYPE, UsleepVoteInfoPayload.CODEC, (payload, context) -> {});
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
        if (!p.connection.hasChannel(UsleepOpenPayload.TYPE)) return false;
        sendSyncTo(p);
        PacketDistributor.sendToPlayer(p, new UsleepOpenPayload());
        return true;
    }

    public static void sendVoteStart(ServerPlayer p, String question, int seconds) {
        if (p.connection.hasChannel(UsleepVoteStartPayload.TYPE)) {
            PacketDistributor.sendToPlayer(p, new UsleepVoteStartPayload(question, seconds));
        }
    }

    public static void sendVoteEnd(ServerPlayer p) {
        if (p.connection.hasChannel(UsleepVoteEndPayload.TYPE)) {
            PacketDistributor.sendToPlayer(p, new UsleepVoteEndPayload());
        }
    }

    public static void sendVoteInfo(ServerPlayer p, int yes, int no, String sleepers) {
        if (p.connection.hasChannel(UsleepVoteInfoPayload.TYPE)) {
            PacketDistributor.sendToPlayer(p, new UsleepVoteInfoPayload(yes, no, sleepers));
        }
    }

    public static void sendSyncTo(ServerPlayer p) {
        if (!p.connection.hasChannel(UsleepSyncPayload.TYPE)) return;
        PacketDistributor.sendToPlayer(p, new UsleepSyncPayload(buildJson(p)));
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
