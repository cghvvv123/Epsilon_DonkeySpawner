package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.MousePressEvent;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

public class KeyboardHUD extends HudModule {

    public static final KeyboardHUD INSTANCE = new KeyboardHUD();

    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.5, 2.5, 0.1);
    private final BoolSetting showMouse = boolSetting("Show Mouse", true);
    private final BoolSetting showCps = boolSetting("Show CPS", true, showMouse::getValue);
    private final ColorSetting pressedColor = colorSetting("Pressed Color", new Color(120, 180, 255, 220));
    private final ColorSetting releasedColor = colorSetting("Released Color", new Color(25, 27, 32, 190));
    private final ColorSetting textColor = colorSetting("Text Color", new Color(245, 247, 250, 250));
    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);
    private final Deque<Long> leftClicks = new ArrayDeque<>();
    private final Deque<Long> rightClicks = new ArrayDeque<>();

    private KeyboardHUD() {
        super("Keyboard HUD", 0f, 0f, 74f, 78f);
    }

    @EventHandler
    private void onMousePress(MousePressEvent event) {
        if (event.getAction() != 1) return;
        if (event.getButton() == 0) leftClicks.addLast(System.currentTimeMillis());
        if (event.getButton() == 1) rightClicks.addLast(System.currentTimeMillis());
    }

    @Override
    public void render(DeltaTracker deltaTracker) {
        if (nullCheck()) return;

        float s = scale.getValue().floatValue();
        float key = 22f * s;
        float gap = 3f * s;

        float width = key * 3f + gap * 2f;
        float height = key * 2f + gap;
        if (showMouse.getValue()) {
            height += key + gap;
        }
        setBounds(width, height);
        drawKeys(width, key, gap, s);
    }

    private void drawKeys(float width, float key, float gap, float s) {
        drawKey("W", this.x + key + gap, this.y, key, key, mc.options.keyUp.isDown(), s);
        drawKey("A", this.x, this.y + key + gap, key, key, mc.options.keyLeft.isDown(), s);
        drawKey("S", this.x + key + gap, this.y + key + gap, key, key, mc.options.keyDown.isDown(), s);
        drawKey("D", this.x + (key + gap) * 2f, this.y + key + gap, key, key, mc.options.keyRight.isDown(), s);
        if (showMouse.getValue()) {
            float mouseY = this.y + key * 2f + gap * 2f;
            float mouseWidth = (width - gap) / 2f;
            drawKey(mouseLabel("LMB", leftClicks), this.x, mouseY, mouseWidth, key, mc.mouseHandler.isLeftPressed(), s);
            drawKey(mouseLabel("RMB", rightClicks), this.x + mouseWidth + gap, mouseY, mouseWidth, key, mc.mouseHandler.isRightPressed(), s);
        }
    }

    private void drawKey(String text, float x, float y, float width, float height, boolean pressed, float scale) {
        renderScope().roundRect(x, y, width, height, 3f * scale, pressed ? pressedColor.getValue() : releasedColor.getValue());
        TextRenderer renderer = textRendererSupplier.get();
        float textScale = 0.55f * scale;
        float textX = x + (width - renderer.getWidth(text, textScale)) / 2f;
        float textY = y + (height - renderer.getHeight(textScale)) / 2f;
        renderScope().text(text, textX, textY, textScale, textColor.getValue());
    }

    private String mouseLabel(String name, Deque<Long> clicks) {
        if (!showCps.getValue()) return name;
        long cutoff = System.currentTimeMillis() - 1000L;
        while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) clicks.removeFirst();
        return name + " " + clicks.size();
    }
}
