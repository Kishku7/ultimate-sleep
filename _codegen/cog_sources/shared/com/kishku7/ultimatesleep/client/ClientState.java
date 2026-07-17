package com.kishku7.ultimatesleep.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client-side cache of the latest settings snapshot synced from the server. */
public final class ClientState {

    private ClientState() {}

    public static final Map<String, String> values = new LinkedHashMap<>();
    public static final Map<String, String> types = new HashMap<>();
    public static final Map<String, List<String>> allowed = new HashMap<>();
    public static final List<String> admins = new ArrayList<>();
    public static volatile String afkOwner = "";
    public static volatile boolean canSet = false;
    public static volatile boolean canAdmin = false;
    public static volatile boolean loaded = false;

    public static void update(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            values.clear();
            types.clear();
            allowed.clear();
            admins.clear();
            JsonObject s = root.getAsJsonObject("settings");
            for (Map.Entry<String, com.google.gson.JsonElement> e : s.entrySet()) {
                values.put(e.getKey(), e.getValue().getAsString());
            }
            JsonObject t = root.getAsJsonObject("types");
            for (Map.Entry<String, com.google.gson.JsonElement> e : t.entrySet()) {
                types.put(e.getKey(), e.getValue().getAsString());
            }
            JsonObject a = root.getAsJsonObject("allowed");
            for (Map.Entry<String, com.google.gson.JsonElement> e : a.entrySet()) {
                List<String> l = new ArrayList<>();
                e.getValue().getAsJsonArray().forEach(x -> l.add(x.getAsString()));
                allowed.put(e.getKey(), l);
            }
            root.getAsJsonArray("admins").forEach(x -> admins.add(x.getAsString()));
            afkOwner = root.get("afkOwner").getAsString();
            canSet = root.get("canSet").getAsBoolean();
            canAdmin = root.get("canAdmin").getAsBoolean();
            loaded = true;
        } catch (Exception ignored) {
            // malformed snapshot -> keep previous state
        }
    }

    public static boolean isBool(String key) { return "BOOL".equals(types.get(key)); }
    public static boolean isEnum(String key) { return "ENUM".equals(types.get(key)); }
}
