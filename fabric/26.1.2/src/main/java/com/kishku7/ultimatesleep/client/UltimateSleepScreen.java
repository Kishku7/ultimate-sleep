package com.kishku7.ultimatesleep.client;

import com.kishku7.ultimatesleep.net.UsleepRosterPayload;
import com.kishku7.ultimatesleep.net.UsleepSetPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Ultimate Sleep admin panel: paginated (8 setting pages + a Sleep-Admin page), reading values
 * from {@link ClientState} (server-synced) and writing over the back-channel; the server validates
 * permissions and re-syncs. CD-themed via the 26.x render pipeline (dark panel + ThemedButtons +
 * dark, borderless edit fields painted by the screen).
 */
public final class UltimateSleepScreen extends Screen {

    private static final String[] PAGE_TITLES = {
            "General", "Sleep Engine", "Voting", "Accessibility", "Feedback",
            "Rewards", "World Progression", "Auto-sleep & AFK", "Sleep Admins"
    };
    private static final String[][] PAGE_KEYS = {
            {"enabled"},
            {"requirement_mode", "required_sleep_percentage", "exclude_afk_from_requirement",
                    "skip_mode", "accelerate_multiplier", "preserve_weather"},
            {"vote_duration_seconds", "vote_pass_rule", "vote_pass_percentage"},
            {"sleep_anytime", "sleep_ignore_monsters", "ignore_bed_too_far", "highlight_blocking_mobs"},
            {"show_sleepers_in_chat", "show_sleepers_on_vote_screen", "notify_wake"},
            {"reward_regeneration", "reward_regeneration_minutes", "reward_golden_carrot",
                    "reward_speed_boost", "reward_speed_boost_percent", "reward_speed_boost_minutes"},
            {"world_progression_enabled", "progress_crops", "progress_animal_husbandry",
                    "progress_smelting", "progress_despawn_timers"},
            {"auto_sleep_enabled", "afk_threshold_seconds", "provide_afk_command"},
            {}
    };
    private static final int ADMIN_PAGE = 8;
    private static final int MAX_ADMIN_ROWS = 5;
    private static final int PW = 260, PH = 204, ROW_TOP_OFF = 28, ROW_H = 22;

    private int page = 0;
    private int adminScroll = 0;
    private final Map<String, EditBox> pageEdits = new LinkedHashMap<>();
    private EditBox addAdminField;

    public UltimateSleepScreen() {
        super(Component.literal("Ultimate Sleep"));
    }

    private int left() { return (this.width - PW) / 2; }
    private int top() { return (this.height - PH) / 2; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int left = left(), top = top();
        g.fill(left - 2, top - 2, left + PW + 2, top + PH + 2, 0xFF000000);
        g.fill(left - 1, top - 1, left + PW + 1, top + PH + 1, 0xFF2F2F2F);
        g.fill(left, top, left + PW, top + PH, 0xFF121212);
        g.fill(left, top, left + PW, top + 22, 0xFF1C1C1C);
        // dark backing for each (borderless) edit field
        for (EditBox eb : pageEdits.values()) paintField(g, eb);
        if (page == ADMIN_PAGE && addAdminField != null) paintField(g, addAdminField);
        // admin-list scrollbar
        if (page == ADMIN_PAGE) {
            int n = ClientState.admins.size();
            if (n > MAX_ADMIN_ROWS) {
                int listTop = top + ROW_TOP_OFF, listH = MAX_ADMIN_ROWS * ROW_H;
                int sx = left + PW - 5;
                g.fill(sx, listTop, sx + 2, listTop + listH, 0xFF000000);
                int thumbH = Math.max(10, listH * MAX_ADMIN_ROWS / n);
                int thumbY = listTop + (listH - thumbH) * adminScroll / Math.max(1, n - MAX_ADMIN_ROWS);
                g.fill(sx, thumbY, sx + 2, thumbY + thumbH, 0xFF9A9A9A);
            }
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private static void paintField(GuiGraphicsExtractor g, EditBox eb) {
        int x = eb.getX(), y = eb.getY(), w = eb.getWidth(), h = eb.getHeight();
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + w, y + h, eb.isFocused() ? 0xFF2A2A2A : 0xFF1E1E1E);
    }

    public void refresh() {
        this.rebuildWidgets();
    }

    @Override
    protected void init() {
        pageEdits.clear();
        addAdminField = null;
        int left = left(), top = top();
        int ctrlW = 92, ctrlX = left + PW - 8 - ctrlW;
        int rowTop = top + ROW_TOP_OFF, navY = top + PH - 26;

        addRenderableWidget(new StringWidget(left, top + 8, PW, 12,
                Component.literal("Ultimate Sleep -- " + PAGE_TITLES[page]), this.font));

        if (!ClientState.loaded) {
            addRenderableWidget(new StringWidget(left, top + PH / 2 - 6, PW, 12,
                    Component.literal("Loading settings..."), this.font));
        } else if (page == ADMIN_PAGE) {
            buildAdminPage(left, rowTop, ctrlW);
        } else {
            String[] keys = PAGE_KEYS[page];
            for (int i = 0; i < keys.length; i++) {
                buildSettingRow(keys[i], left + 8, ctrlX, rowTop + i * ROW_H, ctrlW);
            }
        }

        ThemedButton prev = new ThemedButton(left + 6, navY, 56, 20, Component.literal("< Prev"), () -> {
            commitEdits();
            if (page > 0) { page--; adminScroll = 0; rebuildWidgets(); }
        });
        prev.active = page > 0;
        addRenderableWidget(prev);
        addRenderableWidget(new StringWidget(left + 64, navY + 4, PW - 64 - 120, 12,
                Component.literal("Page " + (page + 1) + " of 9"), this.font));
        ThemedButton next = new ThemedButton(left + PW - 6 - 56 - 4 - 50, navY, 56, 20, Component.literal("Next >"), () -> {
            commitEdits();
            if (page < ADMIN_PAGE) { page++; adminScroll = 0; rebuildWidgets(); }
        });
        next.active = page < ADMIN_PAGE;
        addRenderableWidget(next);
        addRenderableWidget(new ThemedButton(left + PW - 6 - 50, navY, 50, 20, Component.literal("Done"), () -> {
            commitEdits();
            this.onClose();
        }));
    }

    private void buildSettingRow(String key, int labelX, int ctrlX, int y, int ctrlW) {
        addRenderableWidget(new StringWidget(labelX, y + 4, ctrlX - labelX - 4, 12,
                Component.literal(pretty(key)), this.font));
        String val = ClientState.values.getOrDefault(key, "");
        if (ClientState.isBool(key)) {
            boolean on = "true".equalsIgnoreCase(val);
            ThemedButton b = new ThemedButton(ctrlX, y, ctrlW, 20, Component.literal(on ? "ON" : "OFF"),
                    () -> send(key, on ? "false" : "true"));
            b.textColor = on ? 0xFF54FB54 : 0xFF9A9A9A;
            b.active = ClientState.canSet;
            addRenderableWidget(b);
        } else if (ClientState.isEnum(key)) {
            ThemedButton b = new ThemedButton(ctrlX, y, ctrlW, 20, Component.literal("< " + val + " >"),
                    () -> send(key, nextEnum(key)));
            b.textColor = 0xFFFFE14D;
            b.active = ClientState.canSet;
            addRenderableWidget(b);
        } else {
            EditBox eb = new EditBox(this.font, ctrlX, y, ctrlW, 20, Component.literal(pretty(key)));
            eb.setMaxLength(32);
            eb.setValue(val);
            eb.setEditable(ClientState.canSet);
            eb.setBordered(false);
            eb.setTextColor(0xFFE6E6E6);
            pageEdits.put(key, eb);
            addRenderableWidget(eb);
        }
    }

    private void buildAdminPage(int left, int rowTop, int ctrlW) {
        List<String> admins = ClientState.admins;
        if (admins.isEmpty()) {
            addRenderableWidget(new StringWidget(left, rowTop + 6, PW, 12,
                    Component.literal("No sleep admins. Add one below."), this.font));
        } else {
            int maxScroll = Math.max(0, admins.size() - MAX_ADMIN_ROWS);
            if (adminScroll > maxScroll) adminScroll = maxScroll;
            for (int i = 0; i < MAX_ADMIN_ROWS && (adminScroll + i) < admins.size(); i++) {
                String name = admins.get(adminScroll + i);
                int y = rowTop + i * ROW_H;
                addRenderableWidget(new StringWidget(left + 8, y + 4, PW - 8 - 28 - 10, 12,
                        Component.literal(name), this.font));
                ThemedButton rm = new ThemedButton(left + PW - 8 - 20, y, 20, 20, Component.literal("X"),
                        () -> roster("remove", name)).colors(0xFFA04444, 0xFFBB5050, 0xFFFFFFFF);
                rm.active = ClientState.canAdmin;
                addRenderableWidget(rm);
            }
        }
        int addY = rowTop + (MAX_ADMIN_ROWS + 1) * ROW_H;
        addAdminField = new EditBox(this.font, left + 8, addY, PW - 8 - 8 - 50 - 4, 20,
                Component.literal("player name"));
        addAdminField.setMaxLength(16);
        addAdminField.setHint(Component.literal("player name"));
        addAdminField.setEditable(ClientState.canAdmin);
        addAdminField.setBordered(false);
        addAdminField.setTextColor(0xFFE6E6E6);
        addRenderableWidget(addAdminField);
        ThemedButton add = new ThemedButton(left + PW - 8 - 50, addY, 50, 20, Component.literal("Add"), () -> {
            String n = addAdminField.getValue().trim();
            if (!n.isEmpty()) roster("add", n);
        });
        add.active = ClientState.canAdmin;
        addRenderableWidget(add);
    }

    private void commitEdits() {
        for (Map.Entry<String, EditBox> e : pageEdits.entrySet()) {
            String v = e.getValue().getValue();
            if (!v.equals(ClientState.values.getOrDefault(e.getKey(), ""))) {
                send(e.getKey(), v);
            }
        }
    }

    private void send(String key, String value) {
        ClientPlayNetworking.send(new UsleepSetPayload(key, value));
    }

    private void roster(String action, String name) {
        ClientPlayNetworking.send(new UsleepRosterPayload(action, name));
    }

    private static String nextEnum(String key) {
        List<String> opts = ClientState.allowed.get(key);
        if (opts == null || opts.isEmpty()) return ClientState.values.getOrDefault(key, "");
        int i = opts.indexOf(ClientState.values.get(key));
        return opts.get((i + 1 + opts.size()) % opts.size());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (page == ADMIN_PAGE && ClientState.admins.size() > MAX_ADMIN_ROWS) {
            int maxScroll = ClientState.admins.size() - MAX_ADMIN_ROWS;
            adminScroll = Math.max(0, Math.min(maxScroll, adminScroll - (int) Math.signum(scrollY)));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static String pretty(String key) {
        StringBuilder sb = new StringBuilder();
        for (String w : key.split("_")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
