package com.kishku7.ultimatesleep.client;

import com.kishku7.ultimatesleep.net.UsleepVotePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The sleep-vote prompt: a non-pausing prompt near the bottom (above the hotbar) with the question
 * and Yes/No on the right. Voting is sent over the back-channel; closes on a vote, on the server
 * ending the vote, or when the timer runs out. Each voter gets a private win/lost chat message.
 *
 * Widget-only (no custom render) for 26.x render-pipeline compatibility; the dark bottom-bar
 * styling from the CD design is part of the later theming pass.
 */
public final class VoteScreen extends Screen {

    private final String question;
    private int ticksLeft;
    private StringWidget label;

    public VoteScreen(String question, int seconds) {
        super(Component.literal("Sleep Vote"));
        this.question = question;
        this.ticksLeft = Math.max(1, seconds) * 20;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int barW = 330, barH = 34;
        int barX = (this.width - barW) / 2, barY = this.height - 58;
        g.fill(barX - 2, barY - 2, barX + barW + 2, barY + barH + 2, 0xFF000000);
        g.fill(barX, barY, barX + barW, barY + barH, 0xF0121212);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private int secs() {
        return (ticksLeft + 19) / 20;
    }

    @Override
    protected void init() {
        int y = this.height - 52;
        label = new StringWidget(this.width / 2 - 210, y, 320, 20,
                Component.literal(question + "  (" + secs() + "s)"), this.font);
        addRenderableWidget(label);
        int btnW = 46;
        addRenderableWidget(Button.builder(Component.literal("Yes"), b -> castVote(true))
                .bounds(this.width / 2 + 116, y, btnW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("No"), b -> castVote(false))
                .bounds(this.width / 2 + 116 + btnW + 4, y, btnW, 20).build());
    }

    private void castVote(boolean yes) {
        ClientPlayNetworking.send(new UsleepVotePayload(yes));
        onClose();
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
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}
