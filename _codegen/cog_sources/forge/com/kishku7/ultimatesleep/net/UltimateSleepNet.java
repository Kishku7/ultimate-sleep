package com.kishku7.ultimatesleep.net;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kishku7.ultimatesleep.UltimateSleep;
import com.kishku7.ultimatesleep.compat.Era;
import com.kishku7.ultimatesleep.config.Settings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.payload.PayloadProtocol;

import java.util.function.Consumer;

/**
 * Server-side back-channel for the (optional) client admin GUI -- Forge wiring, MODERN twin
 * (1.20.6-1.21.11, Forge 50-61). Mirrors the Fabric/NeoForge UltimateSleepNet 1:1 (same FQCN;
 * per-loader seam class). The S2C payloads are delivered through settable sinks the CLIENT
 * initializer points at the screen cache -- the dedicated server never loads client classes
 * (handlers here only touch the sinks, which default to no-ops; bank-vault pattern).
 *
 * The whole surface -- ChannelBuilder.named(...).payloadChannel().play(), serverbound()/
 * clientbound().add(TYPE, codec, handler), CustomPayloadEvent.Context enqueueWork/getSender,
 * PacketDistributor.PLAYER.with / SERVER.noArg, Channel.isRemotePresent(Connection), and the
 * ServerCommonPacketListenerImpl.getConnection() Forge patch -- is identical on Forge 50..61
 * (javap-verified on the cached recomp jars, 2026-07-05). named() takes ResourceLocation <=60
 * and Identifier at 61: Era.id() already returns the era-correct type. The only cog block is a
 * range guard: a cell below 1.20.5 (SimpleChannel era) fails generation instead of compiling
 * against the wrong API (1.20.1 uses the forge_legacy_net flavour).
 *
 * EVERY consumer must call c.setPacketHandled(true) (fixed 2026-07-05). Forge's plain
 * PayloadFlow.add does NOT auto-set the handled flag (only addMain does); without it
 * NetworkInstance.dispatch returns getPacketHandled()==false, ForgeHooks.onCustomPayload falls
 * through to the vanilla path AFTER the handler already ran, and the client logs
 * "Unknown custom packet payload: ultimate_sleep:sync/:open". The unhandled flag also makes
 * PacketUtils.ensureRunningOnSameThread re-queue the packet off the network thread, so the
 * whole chain (decode + consumer) runs TWICE -- the singleplayer double-decode the sp()
 * wrapper was papering over. Uniform on Forge 50-61 (Sputnic r1/r2 latest.log greps,
 * 2026-07-05): NOT a Forge-60 quirk; the legacy 1.20.1 SimpleChannel flavour is unaffected.
 */
public final class UltimateSleepNet {

    private UltimateSleepNet() {}

    private static final Gson GSON = new Gson();

    /** Replaced by the client initializer; defaults are no-ops (dedicated server). */
    public static Consumer<String> syncSink = json -> {};
    public static Runnable openSink = () -> {};

    private static Channel<CustomPacketPayload> CHANNEL;

    /** Client -> server send seam (the client init points ClientNet.SENDER here). */
    public static void sendToServer(CustomPacketPayload p) {
        CHANNEL.send(p, PacketDistributor.SERVER.noArg());
    }

    private static void sendToPlayer(ServerPlayer player, CustomPacketPayload p) {
        CHANNEL.send(p, PacketDistributor.PLAYER.with(player));
    }

    /** True when the player's client has this channel (i.e. runs the mod). */
    private static boolean canSend(ServerPlayer p) {
        return CHANNEL.isRemotePresent(p.connection.getConnection());
    }

    /** Wrap a codec so a second decode of an already-consumed buffer (single-player double-decode)
     *  rewinds to the payload start rather than overrunning (Forge PayloadChannel quirk;
     *  bank-vault, build-verified 1.20.6-1.21.8). With setPacketHandled(true) in every consumer
     *  the double-dispatch no longer happens; kept as defense in depth. */
    private static <T extends CustomPacketPayload> StreamCodec<RegistryFriendlyByteBuf, T> sp(
            StreamCodec<RegistryFriendlyByteBuf, T> inner) {
        return new StreamCodec<RegistryFriendlyByteBuf, T>() {
            @Override
            public T decode(RegistryFriendlyByteBuf buf) {
                if (buf.readableBytes() == 0 && buf.writerIndex() > 0) buf.readerIndex(0);
                return inner.decode(buf);
            }
            @Override
            public void encode(RegistryFriendlyByteBuf buf, T val) {
                inner.encode(buf, val);
            }
        };
    }

    /** Build + register the payload channel. Called from the @Mod constructor (no register-event on Forge). */
    public static void init() {
        //[[[cog
        //import sys; sys.path.insert(0, codegen); import compat_loaders
        //compat_loaders.forge_modern_net_check(ver)
        //cog.outl('        PayloadProtocol<RegistryFriendlyByteBuf, CustomPacketPayload> proto =')
        //cog.outl('                ChannelBuilder.named(Era.id("main"))')
        //cog.outl('                        .networkProtocolVersion(1)')
        //cog.outl('                        .optional()')
        //cog.outl('                        .payloadChannel()')
        //cog.outl('                        .play();')
        //]]]
        PayloadProtocol<RegistryFriendlyByteBuf, CustomPacketPayload> proto =
                ChannelBuilder.named(Era.id("main"))
                        .networkProtocolVersion(1)
                        .optional()
                        .payloadChannel()
                        .play();
        //[[[end]]]

        // C2S -- each consumer marks the packet handled (see class javadoc).
        proto.serverbound()
                .add(UsleepRequestPayload.TYPE, sp(UsleepRequestPayload.CODEC), (m, c) -> {
                    c.enqueueWork(() -> {
                        ServerPlayer p = c.getSender();
                        if (p != null) sendSyncTo(p);
                    });
                    c.setPacketHandled(true);
                })
                .add(UsleepVotePayload.TYPE, sp(UsleepVotePayload.CODEC), (m, c) -> {
                    c.enqueueWork(() -> {
                        ServerPlayer p = c.getSender();
                        if (p != null) UltimateSleep.vote().castVote(p, m.yes());
                    });
                    c.setPacketHandled(true);
                })
                .add(UsleepSetPayload.TYPE, sp(UsleepSetPayload.CODEC), (m, c) -> {
                    c.enqueueWork(() -> {
                        ServerPlayer p = c.getSender();
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
                    c.setPacketHandled(true);
                })
                .add(UsleepRosterPayload.TYPE, sp(UsleepRosterPayload.CODEC), (m, c) -> {
                    c.enqueueWork(() -> {
                        ServerPlayer p = c.getSender();
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
                    c.setPacketHandled(true);
                });

        // S2C -- handlers run client-side only; the sinks keep client classes off the server.
        CHANNEL = proto.clientbound()
                .add(UsleepSyncPayload.TYPE, sp(UsleepSyncPayload.CODEC), (m, c) -> {
                    c.enqueueWork(() -> syncSink.accept(m.json()));
                    c.setPacketHandled(true);
                })
                .add(UsleepOpenPayload.TYPE, sp(UsleepOpenPayload.CODEC), (m, c) -> {
                    c.enqueueWork(openSink);
                    c.setPacketHandled(true);
                })
                .add(UsleepVoteStartPayload.TYPE, sp(UsleepVoteStartPayload.CODEC), (m, c) -> c.setPacketHandled(true))
                .add(UsleepVoteEndPayload.TYPE, sp(UsleepVoteEndPayload.CODEC), (m, c) -> c.setPacketHandled(true))
                .add(UsleepVoteInfoPayload.TYPE, sp(UsleepVoteInfoPayload.CODEC), (m, c) -> c.setPacketHandled(true))
                .build();
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

    public static void sendVoteStart(ServerPlayer p, String question, int seconds) {
        if (canSend(p)) {
            sendToPlayer(p, new UsleepVoteStartPayload(question, seconds));
        }
    }

    public static void sendVoteEnd(ServerPlayer p) {
        if (canSend(p)) {
            sendToPlayer(p, new UsleepVoteEndPayload());
        }
    }

    public static void sendVoteInfo(ServerPlayer p, int yes, int no, String sleepers) {
        if (canSend(p)) {
            sendToPlayer(p, new UsleepVoteInfoPayload(yes, no, sleepers));
        }
    }

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
