package com.kishku7.ultimatesleep.net;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kishku7.ultimatesleep.UltimateSleep;
import com.kishku7.ultimatesleep.config.Settings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Consumer;

/**
 * Server-side back-channel for the (optional) client admin GUI -- FORGE LEGACY-NET twin
 * (1.20.1, Forge 47): CustomPacketPayload/StreamCodec do not exist yet, and Forge 47's
 * networking is the classic NetworkRegistry/SimpleChannel with indexed message classes and
 * hand-rolled FriendlyByteBuf encode/decode. Same public API as the modern forge
 * UltimateSleepNet so shared command/GUI code compiles unchanged. Handlers hop to the
 * game thread via ctx.enqueueWork before touching state; the S2C messages are delivered
 * through settable sinks the CLIENT initializer points at the screen cache, so the dedicated
 * server never loads client classes. The vestigial vote_start/vote_end/vote_info messages are
 * dropped from the wire protocol on this era (parity with the fabric legacy twin); their
 * senders remain as no-op stubs.
 *
 * NOTE: NetworkRegistry.newSimpleChannel IS the era API on Forge 47 (deprecated-for-removal
 * upstream but never removed on the 1.20.1 line) -- bank-vault shipped this exact shape.
 */
public final class UltimateSleepNet {

    private UltimateSleepNet() {}

    private static final Gson GSON = new Gson();

    /** Replaced by the client initializer; defaults are no-ops (dedicated server). */
    public static Consumer<String> syncSink = json -> {};
    public static Runnable openSink = () -> {};

    @SuppressWarnings("removal")   // NetworkRegistry/SimpleChannel IS the 1.20.1 (forge 47) networking API
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("ultimate_sleep", "main"), () -> "1", v -> true, v -> true);

    /** Client -> server send seam (the client init points ClientNet.SENDER here). */
    public static void sendToServer(Object payload) {
        CHANNEL.sendToServer(payload);
    }

    private static void sendToPlayer(ServerPlayer player, Object payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    /** True when the player's client has this channel (i.e. runs the mod).
     *  ServerGamePacketListenerImpl.connection is a public field on 1.20.1. */
    private static boolean canSend(ServerPlayer p) {
        return CHANNEL.isRemotePresent(p.connection.connection);
    }

    /** Register every message on the classic SimpleChannel. Called from the @Mod constructor. */
    public static void init() {
        int i = 0;
        // C2S
        CHANNEL.registerMessage(i++, UsleepRequestPayload.class,
                UsleepRequestPayload::write, UsleepRequestPayload::decode,
                (m, ctx) -> {
                    ctx.get().enqueueWork(() -> {
                        ServerPlayer p = ctx.get().getSender();
                        if (p != null) sendSyncTo(p);
                    });
                    ctx.get().setPacketHandled(true);
                });
        CHANNEL.registerMessage(i++, UsleepVotePayload.class,
                UsleepVotePayload::write, UsleepVotePayload::decode,
                (m, ctx) -> {
                    ctx.get().enqueueWork(() -> {
                        ServerPlayer p = ctx.get().getSender();
                        if (p != null) UltimateSleep.vote().castVote(p, m.yes());
                    });
                    ctx.get().setPacketHandled(true);
                });
        CHANNEL.registerMessage(i++, UsleepSetPayload.class,
                UsleepSetPayload::write, UsleepSetPayload::decode,
                (m, ctx) -> {
                    ctx.get().enqueueWork(() -> {
                        ServerPlayer p = ctx.get().getSender();
                        if (p == null) return;
                        if (UltimateSleep.permissions().canSet(p.createCommandSourceStack())) {
                            String err = UltimateSleep.settings().setRaw(m.key(), m.value());
                            if (err == null) {
                                UltimateSleep.settings().save();
                                MinecraftServer s = p.level().getServer();
                                if (s != null) UltimateSleep.engine().applyConfig(s);
                            }
                        }
                        broadcast(p);
                    });
                    ctx.get().setPacketHandled(true);
                });
        CHANNEL.registerMessage(i++, UsleepRosterPayload.class,
                UsleepRosterPayload::write, UsleepRosterPayload::decode,
                (m, ctx) -> {
                    ctx.get().enqueueWork(() -> {
                        ServerPlayer p = ctx.get().getSender();
                        if (p == null) return;
                        if (UltimateSleep.permissions().canAdmin(p.createCommandSourceStack())) {
                            if ("add".equalsIgnoreCase(m.action())) {
                                UltimateSleep.permissions().addAdmin(m.name());
                            } else if ("remove".equalsIgnoreCase(m.action())) {
                                UltimateSleep.permissions().removeAdmin(m.name());
                            }
                        }
                        broadcast(p);
                    });
                    ctx.get().setPacketHandled(true);
                });
        // S2C -- handlers run client-side only; the sinks keep client classes off the server.
        CHANNEL.registerMessage(i++, UsleepSyncPayload.class,
                UsleepSyncPayload::write, UsleepSyncPayload::decode,
                (m, ctx) -> {
                    ctx.get().enqueueWork(() -> syncSink.accept(m.json()));
                    ctx.get().setPacketHandled(true);
                });
        CHANNEL.registerMessage(i++, UsleepOpenPayload.class,
                UsleepOpenPayload::write, UsleepOpenPayload::decode,
                (m, ctx) -> {
                    ctx.get().enqueueWork(openSink);
                    ctx.get().setPacketHandled(true);
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
        if (!canSend(p)) return false;
        sendSyncTo(p);
        sendToPlayer(p, new UsleepOpenPayload());
        return true;
    }

    /** No-op on this era: the vote_start message is dropped (no client listens). API parity only. */
    public static void sendVoteStart(ServerPlayer p, String question, int seconds) {}

    /** No-op on this era: the vote_end message is dropped. API parity only. */
    public static void sendVoteEnd(ServerPlayer p) {}

    /** No-op on this era: the vote_info message is dropped. API parity only. */
    public static void sendVoteInfo(ServerPlayer p, int yes, int no, String sleepers) {}

    public static void sendSyncTo(ServerPlayer p) {
        if (!canSend(p)) return;
        sendToPlayer(p, new UsleepSyncPayload(buildJson(p)));
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
