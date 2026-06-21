package com.kishku7.ultimatesleep.client;

import com.kishku7.ultimatesleep.net.UsleepVotePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The sleep-vote prompt: a non-pausing dark bar near the bottom (above the hotbar) with the
 * question on the left and green Yes / red No on the right. Voting goes over the back-channel;
 * closes on a vote, on the server ending the vote, or when the timer runs out. When the server
 * pushes live vote info (show_sleepers_on_vote_screen), a tally + sleeper line shows above the bar.
 */
public final class VoteScreen extends Screen {

    /** Latest server-pushed "Yes N / No M -- In bed: ..." line; empty hides it. */
    public static volatile String infoLine = "";

    private static final int BAR_W = 330, BAR_H = 34;
    private final String question;
    private int ticksLeft;
    private StringWidget label;
    private StringWidget info;

    public VoteScreen(String question, int seconds) {
        super(Component.literal("Sleep Vote"));
        this.question = question;
        this.ticksLeft = Math.max(1, seconds) * 20;
    }

    private int secs() { return (ticksLeft + 19) / 20; }
    private int barX() { return (this.width - BAR_W) / 2; }
    private int barY() { return this.height - 58; }

    @Override
    protected void init() {
        int y = barY() + 7;
        info = new StringWidget(barX() + 4, barY() - 13, BAR_W - 8, 10,
                Component.literal(infoLine), this.font);
        addRenderableWidget(info);
        label = new StringWidget(barX() + 8, y + 3, BAR_W - 8 - 100, 12,
                Component.literal(question + "  (" + secs() + "s)"), this.font);
        addRenderableWidget(label);
        int btnW = 44;
        ThemedButton yes = new ThemedButton(barX() + BAR_W - 8 - btnW * 2 - 4, y, btnW, 20,
                Component.literal("Yes"), () -> castVote(true)).colors(0xFF5F9A3F, 0xFF6CAB48, 0xFFFFFFFF);
        ThemedButton no = new ThemedButton(barX() + BAR_W - 8 - btnW, y, btnW, 20,
                Component.literal("No"), () -> castVote(false)).colors(0xFFB04A4A, 0xFFC25454, 0xFFFFFFFF);
        addRenderableWidget(yes);
        addRenderableWidget(no);
    }

    private void castVote(boolean yes) {
        ClientPlayNetworking.send(new UsleepVotePayload(yes));
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = barX(), y = barY();
        g.fill(x - 2, y - 2, x + BAR_W + 2, y + BAR_H + 2, 0xFF000000);
        g.fill(x, y, x + BAR_W, y + BAR_H, 0xF0121212);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        if (--ticksLeft <= 0) {
            onClose();
            return;
        }
        if (label != null) {
            label.setMessage(Component.literal(question + "  (" + secs() + "s)"));
        }
        if (info != null) {
            info.setMessage(Component.literal(infoLine));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        infoLine = "";
        Minecraft.getInstance().setScreen(null);
    }
}
