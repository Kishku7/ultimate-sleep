package com.kishku7.ultimatesleep.client;

import com.kishku7.ultimatesleep.net.UsleepRosterPayload;
import com.kishku7.ultimatesleep.net.UsleepSetPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Ultimate Sleep admin panel. Paginated (8 setting pages + a Sleep-Admin page), built from
 * vanilla widgets. Reads current values from {@link ClientState} (synced from the server) and
 * writes changes via the back-channel payloads; the server validates permissions and re-syncs.
 *
 * Widget-only (no custom render) for compatibility with the 26.x GUI render pipeline. Pixel-exact
 * CD theming is a later visual pass.
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
            {} // admin page
    };
    private static final int ADMIN_PAGE = 8;
    private static final int MAX_ADMIN_ROWS = 5;

    private int page = 0;
    private int adminScroll = 0;
    private final Map<String, EditBox> pageEdits = new LinkedHashMap<>();
    private EditBox addAdminField;

    public UltimateSleepScreen() {
        super(Component.literal("Ultimate Sleep"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int pW = 260, pH = 204;
        int left = (this.width - pW) / 2, top = (this.height - pH) / 2;
        g.fill(left - 2, top - 2, left + pW + 2, top + pH + 2, 0xFF000000);
        g.fill(left - 1, top - 1, left + pW + 1, top + pH + 1, 0xFF2F2F2F);
        g.fill(left, top, left + pW, top + pH, 0xFF121212);
        g.fill(left, top, left + pW, top + 22, 0xFF1C1C1C);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    public void refresh() {
        this.rebuildWidgets();
    }

    @Override
    protected void init() {
        pageEdits.clear();
        addAdminField = null;

        int panelW = 260, panelH = 204;
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;
        int ctrlW = 92;
        int ctrlX = left + panelW - 8 - ctrlW;
        int rowTop = top + 28;
        int rowH = 22;
        int navY = top + panelH - 26;

        addRenderableWidget(new StringWidget(left, top + 8, panelW, 12,
                Component.literal("Ultimate Sleep -- " + PAGE_TITLES[page]), this.font));

        if (!ClientState.loaded) {
            addRenderableWidget(new StringWidget(left, top + panelH / 2 - 6, panelW, 12,
                    Component.literal("Loading settings..."), this.font));
        } else if (page == ADMIN_PAGE) {
            buildAdminPage(left, panelW, rowTop, rowH, ctrlW);
        } else {
            String[] keys = PAGE_KEYS[page];
            for (int i = 0; i < keys.length; i++) {
                buildSettingRow(keys[i], left + 8, ctrlX, rowTop + i * rowH, ctrlW);
            }
        }

        // navigation
        Button prev = Button.builder(Component.literal("< Prev"), b -> {
            commitEdits();
            if (page > 0) { page--; adminScroll = 0; rebuildWidgets(); }
        }).bounds(left + 6, navY, 56, 20).build();
        prev.active = page > 0;
        addRenderableWidget(prev);

        addRenderableWidget(new StringWidget(left + 64, navY + 4, panelW - 64 - 120, 12,
                Component.literal("Page " + (page + 1) + " of 9"), this.font));

        Button next = Button.builder(Component.literal("Next >"), b -> {
            commitEdits();
            if (page < ADMIN_PAGE) { page++; adminScroll = 0; rebuildWidgets(); }
        }).bounds(left + panelW - 6 - 56 - 4 - 50, navY, 56, 20).build();
        next.active = page < ADMIN_PAGE;
        addRenderableWidget(next);

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> {
            commitEdits();
            this.onClose();
        }).bounds(left + panelW - 6 - 50, navY, 50, 20).build());
    }

    private void buildSettingRow(String key, int labelX, int ctrlX, int y, int ctrlW) {
        addRenderableWidget(new StringWidget(labelX, y + 4, ctrlX - labelX - 4, 12,
                Component.literal(pretty(key)), this.font));
        String val = ClientState.values.getOrDefault(key, "");

        if (ClientState.isBool(key)) {
            boolean on = "true".equalsIgnoreCase(val);
            Button b = Button.builder(Component.literal(on ? "ON" : "OFF"), btn ->
                    send(key, on ? "false" : "true")).bounds(ctrlX, y, ctrlW, 20).build();
            b.active = ClientState.canSet;
            addRenderableWidget(b);
        } else if (ClientState.isEnum(key)) {
            Button b = Button.builder(Component.literal(val), btn -> send(key, nextEnum(key)))
                    .bounds(ctrlX, y, ctrlW, 20).build();
            b.active = ClientState.canSet;
            addRenderableWidget(b);
        } else {
            EditBox eb = new EditBox(this.font, ctrlX, y, ctrlW, 20, Component.literal(pretty(key)));
            eb.setMaxLength(32);
            eb.setValue(val);
            eb.setEditable(ClientState.canSet);
            pageEdits.put(key, eb);
            addRenderableWidget(eb);
        }
    }

    private void buildAdminPage(int left, int panelW, int rowTop, int rowH, int ctrlW) {
        List<String> admins = ClientState.admins;
        if (admins.isEmpty()) {
            addRenderableWidget(new StringWidget(left, rowTop + 6, panelW, 12,
                    Component.literal("No sleep admins. Add one below."), this.font));
        } else {
            int maxScroll = Math.max(0, admins.size() - MAX_ADMIN_ROWS);
            if (adminScroll > maxScroll) adminScroll = maxScroll;
            for (int i = 0; i < MAX_ADMIN_ROWS && (adminScroll + i) < admins.size(); i++) {
                String name = admins.get(adminScroll + i);
                int y = rowTop + i * rowH;
                addRenderableWidget(new StringWidget(left + 8, y + 4, panelW - 8 - 28 - 8, 12,
                        Component.literal(name), this.font));
                Button rm = Button.builder(Component.literal("X"), b ->
                        roster("remove", name)).bounds(left + panelW - 8 - 20, y, 20, 20).build();
                rm.active = ClientState.canAdmin;
                addRenderableWidget(rm);
            }
            if (admins.size() > MAX_ADMIN_ROWS) {
                addRenderableWidget(new StringWidget(left, rowTop + MAX_ADMIN_ROWS * rowH, panelW, 12,
                        Component.literal("(scroll for more -- " + admins.size() + " total)"), this.font));
            }
        }

        int addY = rowTop + (MAX_ADMIN_ROWS + 1) * rowH;
        addAdminField = new EditBox(this.font, left + 8, addY, panelW - 8 - 8 - 50 - 4, 20,
                Component.literal("player name"));
        addAdminField.setMaxLength(16);
        addAdminField.setHint(Component.literal("player name"));
        addAdminField.setEditable(ClientState.canAdmin);
        addRenderableWidget(addAdminField);
        Button add = Button.builder(Component.literal("Add"), b -> {
            String n = addAdminField.getValue().trim();
            if (!n.isEmpty()) roster("add", n);
        }).bounds(left + panelW - 8 - 50, addY, 50, 20).build();
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
            int next = adminScroll - (int) Math.signum(scrollY);
            adminScroll = Math.max(0, Math.min(maxScroll, next));
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
