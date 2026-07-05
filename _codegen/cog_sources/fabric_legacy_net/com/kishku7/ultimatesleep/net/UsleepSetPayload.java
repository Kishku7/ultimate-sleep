package com.kishku7.ultimatesleep.net;

/**
 * Client -> server: set one setting (server validates permission, applies, re-syncs).
 * Legacy-net twin: plain holder record (NOT a CustomPacketPayload -- that interface does not
 * exist on 1.20-1.20.4). Same ctor + accessor shapes as the modern record so shared GUI code
 * (UltimateSleepScreen) compiles unchanged. Wire form: UsleepPayloads.SET.
 */
public record UsleepSetPayload(String key, String value) {}
