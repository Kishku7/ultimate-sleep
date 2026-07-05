package com.kishku7.ultimatesleep.net;

/**
 * Client -> server: manage the sleep-admin roster. action = "add" | "remove".
 * Legacy-net twin: plain holder record (NOT a CustomPacketPayload). Same ctor + accessor shapes
 * as the modern record so shared GUI code compiles unchanged. Wire form: UsleepPayloads.ROSTER.
 */
public record UsleepRosterPayload(String action, String name) {}
