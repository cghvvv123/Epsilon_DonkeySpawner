package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;

import java.awt.*;
import java.util.Locale;
import java.util.function.Supplier;

public class LagNotifierHUD extends HudModule {

    public static final LagNotifierHUD INSTANCE = new LagNotifierHUD();

    private enum HorizontalAlignment {
        Left,
        Center,
        Right
    }

    private final DoubleSetting threshold = doubleSetting("Threshold", 1.1, 0.5, 10.0, 0.1);
    private final DoubleSetting scale = doubleSetting("Scale", 0.75, 0.4, 2.0, 0.05);
    private final EnumSetting<HorizontalAlignment> alignment = enumSetting("Alignment", HorizontalAlignment.Left);
    private final ColorSetting normalColor = colorSetting("Normal Color", new Color(255, 220, 40, 255));
    private final ColorSetting warningColor = colorSetting("Warning Color", new Color(255, 145, 40, 255));
    private final ColorSetting criticalColor = colorSetting("Critical Color", new Color(240, 55, 55, 255));
    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);
    private long lastServerTickAt;

    private LagNotifierHUD() {
        super("Lag Notifier HUD", 0f, 0f, 110f, 14f);
    }

    @Override
    protected boolean shouldRenderTextShadow() {
        return true;
    }

    @Override
    protected HorizontalAnchor getResizeHorizontalAnchor() {
        return switch (alignment.getValue()) {
            case Left -> HorizontalAnchor.Left;
            case Center -> HorizontalAnchor.Center;
            case Right -> HorizontalAnchor.Right;
        };
    }

    @Override
    protected void onEnable() {
        lastServerTickAt = System.currentTimeMillis();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundSetTimePacket) lastServerTickAt = System.currentTimeMillis();
    }

    @Override
    public void render(DeltaTracker deltaTracker) {
        if (nullCheck()) return;

        double seconds = (System.currentTimeMillis() - lastServerTickAt) / 1000.0;
        if (seconds < threshold.getValue()) {
            setBounds(20f, 12f);
            return;
        }

        String text = "Server has not ticked for " + String.format(Locale.ROOT, "%.1f", seconds) + "s";
        float textScale = scale.getValue().floatValue();
        TextRenderer renderer = textRendererSupplier.get();
        Color color = seconds > 10.0 ? criticalColor.getValue() : seconds > 3.0 ? warningColor.getValue() : normalColor.getValue();
        float width = Math.max(110f, renderer.getWidth(text, textScale));
        setBounds(width, renderer.getHeight(textScale));
        float textX = this.x + switch (alignment.getValue()) {
            case Left -> 0f;
            case Center -> (width - renderer.getWidth(text, textScale)) / 2f;
            case Right -> width - renderer.getWidth(text, textScale);
        };
        renderScope().text(text, textX, this.y, textScale, color);
    }
}
