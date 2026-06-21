package com.kishku7.ultimatesleep.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A CD-styled button (fully custom-rendered via the 26.x pipeline): dark face with an inset bevel
 * (light top/left, dark bottom/right), black outer border, hover-brighten, disabled-grey, and a
 * configurable face + label color. Used for toggles (green/grey ON/OFF), cycle selectors (yellow),
 * nav/admin (grey), the red admin-remove, and the green/red vote buttons.
 */
public final class ThemedButton extends AbstractWidget {

    private final Runnable action;
    public int textColor = 0xFFE6E6E6;
    public int bgColor = 0xFF8A8A8A;
    public int bgHover = 0xFF9C9C9C;

    public ThemedButton(int x, int y, int w, int h, Component msg, Runnable action) {
        super(x, y, w, h, msg);
        this.action = action;
    }

    public ThemedButton colors(int bg, int hover, int text) {
        this.bgColor = bg;
        this.bgHover = hover;
        this.textColor = text;
        return this;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        action.run();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hov = this.active && this.isMouseOver(mouseX, mouseY);
        int bg = !this.active ? 0xFF4A4A4A : (hov ? bgHover : bgColor);
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFB6B6B6);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFFB6B6B6);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF4D4D4D);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, 0xFF4D4D4D);
        g.centeredText(Minecraft.getInstance().font, getMessage(), x + w / 2, y + (h - 8) / 2,
                this.active ? textColor : 0xFF7A7A7A);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
