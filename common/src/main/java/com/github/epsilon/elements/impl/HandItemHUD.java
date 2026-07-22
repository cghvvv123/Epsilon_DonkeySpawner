package com.github.epsilon.elements.impl;

import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.elements.HudModule;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import java.awt.*;
import java.util.function.Supplier;

public abstract class HandItemHUD extends HudModule {

    protected enum HorizontalAlignment {
        Left,
        Center,
        Right
    }

    protected final DoubleSetting scale = doubleSetting("Scale", 0.72, 0.4, 2.0, 0.02);
    protected final DoubleSetting padding = doubleSetting("Padding", 5.0, 0.0, 16.0, 0.5);
    protected final EnumSetting<HorizontalAlignment> alignment = enumSetting("Alignment", HorizontalAlignment.Left);
    protected final BoolSetting background = boolSetting("Background", true);
    protected final ColorSetting backgroundColor = colorSetting("Background Color", new Color(15, 15, 15, 150), background::getValue);
    protected final ColorSetting itemColor = colorSetting("Item Color", new Color(245, 247, 250, 250));
    protected final ColorSetting durabilityColor = colorSetting("Durability Color", new Color(170, 180, 195, 235));
    protected final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);
    private TranslateComponent emptyTranslation;
    private TranslateComponent unbreakableTranslation;

    protected HandItemHUD(String name) {
        super(name, 0f, 0f, 150f, 34f);
    }

    @Override
    protected boolean shouldRenderTextShadow() {
        return !background.getValue();
    }

    @Override
    protected HorizontalAnchor getResizeHorizontalAnchor() {
        return switch (alignment.getValue()) {
            case Left -> HorizontalAnchor.Left;
            case Center -> HorizontalAnchor.Center;
            case Right -> HorizontalAnchor.Right;
        };
    }

    protected abstract ItemStack getStack();

    protected final String emptyText() {
        if (translateComponent == null) return "Empty";
        if (emptyTranslation == null) emptyTranslation = translateComponent.createChild("display.empty");
        return emptyTranslation.getTranslatedName();
    }

    protected final String unbreakableText() {
        if (translateComponent == null) return "Unbreakable";
        if (unbreakableTranslation == null) unbreakableTranslation = translateComponent.createChild("display.unbreakable");
        return unbreakableTranslation.getTranslatedName();
    }

    @Override
    public void render(DeltaTracker deltaTracker) {
        TextRenderer renderer = textRendererSupplier.get();
        float textScale = scale.getValue().floatValue();
        float pad = padding.getValue().floatValue();
        ItemStack stack = getStack();
        String itemName = stack.isEmpty() ? emptyText() : stack.getHoverName().getString();
        String durability = durabilityText(stack);
        float itemWidth = renderer.getWidth(itemName, textScale);
        float durabilityWidth = renderer.getWidth(durability, textScale);
        // 面板宽度固定，不随文字内容变化；文字可超出框范围
        float width = 150f;
        float height = pad * 2f + renderer.getHeight(textScale) * 2f;
        setBounds(width, height);
        if (background.getValue()) renderScope().roundRect(this.x, this.y, width, height, 4f, backgroundColor.getValue());

        float itemX = alignedX(width, itemWidth, pad);
        float durabilityX = alignedX(width, durabilityWidth, pad);
        renderScope().text(itemName, itemX, this.y + pad, textScale, itemColor.getValue());
        renderScope().text(durability, durabilityX, this.y + pad + renderer.getHeight(textScale), textScale, durabilityColor(stack));
    }

    private float alignedX(float width, float textWidth, float pad) {
        float available = width - pad * 2f - textWidth;
        return this.x + pad + switch (alignment.getValue()) {
            case Left -> 0f;
            case Center -> available / 2f;
            case Right -> available;
        };
    }

    private String durabilityText(ItemStack stack) {
        if (stack.isEmpty()) return "N/A";
        if (stack.has(DataComponents.UNBREAKABLE)) return unbreakableText();
        if (!stack.isDamageableItem()) return "N/A";
        return (stack.getMaxDamage() - stack.getDamageValue()) + " / " + stack.getMaxDamage();
    }

    private Color durabilityColor(ItemStack stack) {
        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) return durabilityColor.getValue();
        float percentage = Math.max(0f, Math.min(1f,
                (stack.getMaxDamage() - stack.getDamageValue()) / (float) stack.getMaxDamage()));
        Color low = new Color(235, 65, 55, 255);
        Color middle = new Color(245, 210, 60, 255);
        Color high = new Color(70, 210, 105, 255);
        return percentage < 0.5f
                ? lerp(low, middle, percentage * 2f)
                : lerp(middle, high, (percentage - 0.5f) * 2f);
    }

    private Color lerp(Color from, Color to, float delta) {
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * delta),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * delta),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * delta),
                Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * delta)
        );
    }

    public static final class MainHandHUD extends HandItemHUD {
        public static final MainHandHUD INSTANCE = new MainHandHUD();

        private MainHandHUD() {
            super("Main Hand Item HUD");
        }

        @Override
        protected ItemStack getStack() {
            return mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
        }
    }

    public static final class OffHandHUD extends HandItemHUD {
        public static final OffHandHUD INSTANCE = new OffHandHUD();

        private OffHandHUD() {
            super("Off Hand Item HUD");
        }

        @Override
        protected ItemStack getStack() {
            return mc.player == null ? ItemStack.EMPTY : mc.player.getOffhandItem();
        }
    }
}
