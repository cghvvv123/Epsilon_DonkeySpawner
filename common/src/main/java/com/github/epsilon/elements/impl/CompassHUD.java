package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.util.Mth;

import java.awt.*;
import java.util.function.Supplier;

public class CompassHUD extends HudModule {

    public static final CompassHUD INSTANCE = new CompassHUD();

    private final DoubleSetting widthSetting = doubleSetting("Width", 150.0, 60.0, 300.0, 2.0);
    private final DoubleSetting scale = doubleSetting("Scale", 0.75, 0.4, 2.0, 0.05);
    private final ColorSetting primaryColor = colorSetting("Primary Color", new Color(245, 247, 250, 250));
    private final ColorSetting secondaryColor = colorSetting("Secondary Color", new Color(150, 160, 178, 210));
    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);

    private CompassHUD() {
        super("Compass HUD", 0f, 0f, 150f, 18f);
    }

    @Override
    public void render(DeltaTracker deltaTracker) {
        if (nullCheck()) return;

        TextRenderer renderer = textRendererSupplier.get();
        float width = widthSetting.getValue().floatValue();
        float textScale = scale.getValue().floatValue();
        float height = renderer.getHeight(textScale) + 5f;
        float yaw = Mth.wrapDegrees(mc.player.getYRot());

        String[] names = {"S", "W", "N", "E", "S", "W", "N"};
        for (int i = 0; i < names.length; i++) {
            float directionYaw = -180f + i * 90f;
            float difference = Mth.wrapDegrees(directionYaw - yaw);
            if (Math.abs(difference) > 135f) continue;
            float centerX = this.x + width / 2f + difference / 135f * width / 2f;
            float textWidth = renderer.getWidth(names[i], textScale);
            Color color = Math.abs(difference) < 45f ? primaryColor.getValue() : secondaryColor.getValue();
            renderScope().text(names[i], centerX - textWidth / 2f, this.y + 2f, textScale, color);
        }

        renderScope().roundRect(this.x + width / 2f - 1f, this.y, 2f, 2f, 1f, primaryColor.getValue());
        setBounds(width, height);
    }
}
