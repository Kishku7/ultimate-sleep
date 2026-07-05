package com.kishku7.ultimatesleep.net;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Legacy-net era (MC 1.20-1.20.4): CustomPacketPayload/StreamCodec do not exist yet, so the wire
 * protocol is plain fabric-networking-api-v1 channels keyed by ResourceLocation, with hand-rolled
 * FriendlyByteBuf encode/decode. Channel ids match the modern payload ids one-to-one. The three
 * vestigial vote_start/vote_end/vote_info channels are dropped entirely on this era (nothing on
 * either side sends or listens; UltimateSleepNet keeps no-op stubs for API parity).
 */
public final class UsleepPayloads {

    private UsleepPayloads() {}

    private static final String NS = "ultimate_sleep";

    /** Client -> server: "send me the current settings snapshot." Empty body. */
    public static final ResourceLocation REQUEST = new ResourceLocation(NS, "request");

    /** Client -> server: set one setting. Body: key + value (UTF strings). */
    public static final ResourceLocation SET = new ResourceLocation(NS, "set");

    /** Client -> server: manage the admin roster. Body: action ("add"|"remove") + name. */
    public static final ResourceLocation ROSTER = new ResourceLocation(NS, "roster");

    /** Client -> server: cast a sleep vote. Body: one boolean. */
    public static final ResourceLocation VOTE = new ResourceLocation(NS, "vote");

    /** Server -> client: full settings snapshot. Body: one JSON UTF string. */
    public static final ResourceLocation SYNC = new ResourceLocation(NS, "sync");

    /** Server -> client: open the admin panel. Empty body. */
    public static final ResourceLocation OPEN = new ResourceLocation(NS, "open");

    /** Fresh empty body (request/open). */
    public static FriendlyByteBuf empty() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    public static FriendlyByteBuf encodeSet(UsleepSetPayload p) {
        FriendlyByteBuf buf = empty();
        buf.writeUtf(p.key());
        buf.writeUtf(p.value());
        return buf;
    }

    public static UsleepSetPayload decodeSet(FriendlyByteBuf buf) {
        return new UsleepSetPayload(buf.readUtf(), buf.readUtf());
    }

    public static FriendlyByteBuf encodeRoster(UsleepRosterPayload p) {
        FriendlyByteBuf buf = empty();
        buf.writeUtf(p.action());
        buf.writeUtf(p.name());
        return buf;
    }

    public static UsleepRosterPayload decodeRoster(FriendlyByteBuf buf) {
        return new UsleepRosterPayload(buf.readUtf(), buf.readUtf());
    }

    public static FriendlyByteBuf encodeVote(UsleepVotePayload p) {
        FriendlyByteBuf buf = empty();
        buf.writeBoolean(p.yes());
        return buf;
    }

    public static UsleepVotePayload decodeVote(FriendlyByteBuf buf) {
        return new UsleepVotePayload(buf.readBoolean());
    }

    public static FriendlyByteBuf encodeSync(String json) {
        FriendlyByteBuf buf = empty();
        buf.writeUtf(json);
        return buf;
    }

    public static String decodeSync(FriendlyByteBuf buf) {
        return buf.readUtf();
    }
}
