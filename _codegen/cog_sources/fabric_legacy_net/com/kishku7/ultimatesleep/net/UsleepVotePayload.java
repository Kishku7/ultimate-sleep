package com.kishku7.ultimatesleep.net;

/**
 * Client -> server: cast a sleep vote (Yes/No).
 * Legacy-net twin: plain holder record (NOT a CustomPacketPayload). Same ctor + accessor shapes
 * as the modern record. Wire form: UsleepPayloads.VOTE.
 */
public record UsleepVotePayload(boolean yes) {}
