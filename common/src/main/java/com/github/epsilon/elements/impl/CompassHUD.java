package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.util.Mth;

import java.awt.*;
import java.util.function.Supplier;

public class CompassHUD extends HudModule {

    public static final CompassHUD INSTANCE = new CompassHUD();

    private enum DisplayMode {
        NWES("NWES"),
        Axis("\u00B1XZ");

        private final String title;

        DisplayMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private static final class DisplayModeSetting extends EnumSetting<DisplayMode> {
        private DisplayModeSetting() {
            super("Display Mode", DisplayMode.NWES, () -> true, null);
        }

        @Override
        public String getTranslatedValue() {
            return getValue().toString();
        }

        @Override
        public String getTranslatedValueByIndex(int index) {
            DisplayMode[] modes = getModes();
            return index >= 0 && index < modes.length ? modes[index].toString() : "";
        }

        @Override
        public String getTranslatedValue(DisplayMode mode) {
            return mode.toString();
        }

        @Override
        public String getTranslatedValueUnchecked(Enum<?> mode) {
            return mode instanceof DisplayMode displayMode ? displayMode.toString() : "";
        }
    }

    private final EnumSetting<DisplayMode> displayMode = addSetting(new DisplayModeSetting());
    private final DoubleSetting widthSetting = doubleSetting("Width", 150.0, 60.0, 300.0, 2.0);
    private final DoubleSetting scale = doubleSetting("Scale", 0.75, 0.4, 2.0, 0.05);
    private final ColorSetting primaryColor = colorSetting("Primary Color", new Color(245, 247, 250, 250));
    private final ColorSetting secondaryColor = colorSetting("Secondary Color", new Color(150, 160, 178, 210));
    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);

    private CompassHUD() {
        super("Compass HUD", 0f, 0f, 150f, 18f);
    }

    @Override
    protected boolean shouldRenderTextShadow() {
        return true;
    }

    @Override
    public void render(DeltaTracker deltaTracker) {
        if (nullCheck()) return;

        TextRenderer renderer = textRendererSupplier.get();
        float width = widthSetting.getValue().floatValue();
        float textScale = scale.getValue().floatValue();
        float height = renderer.getHeight(textScale) + 5f;
        float yaw = Mth.wrapDegrees(mc.player.getYRot());
        setBounds(width, height);

        String[] cardinalNames = {"S", "W", "N", "E"};
        String[] axisNames = {"+Z", "-X", "-Z", "+X"};
        float[] directionYaws = {0f, 90f, 180f, -90f};
        for (int i = 0; i < cardinalNames.length; i++) {
            float difference = Mth.wrapDegrees(directionYaws[i] - yaw);
            if (Math.abs(difference) > 135f) continue;
            String name = displayMode.is(DisplayMode.NWES) ? cardinalNames[i] : axisNames[i];
            boolean north = i == 2;
            float centerX = this.x + width / 2f + difference / 135f * width / 2f;
            float textWidth = renderer.getWidth(name, textScale);
            Color color = north
                    ? (Math.abs(difference) < 45f ? new Color(240, 55, 65, 255) : new Color(135, 35, 45, 220))
                    : (Math.abs(difference) < 45f ? primaryColor.getValue() : secondaryColor.getValue());
            renderScope().text(name, centerX - textWidth / 2f, this.y + 2f, textScale, color);
        }

        renderScope().roundRect(this.x + width / 2f - 1f, this.y, 2f, 2f, 1f, primaryColor.getValue());
    }
}
