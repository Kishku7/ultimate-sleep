package com.kishku7.ultimatesleep.client;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint -- reserved for the in-game admin panel GUI.
 *
 * Planned flow (see FUNCTIONAL_SPEC.md):
 *   1. On open, the panel runs "/usleep admin query" to fetch the current
 *      settings and learn which mod owns the /afk command.
 *   2. Each control change is applied by sending
 *      "/usleep admin set &lt;key&gt; &lt;value&gt;".
 *
 * The GUI itself is intentionally not implemented yet -- it will be designed
 * once the full feature/setting set is locked in.
 */
public final class UltimateSleepClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // no-op for now
    }
}
