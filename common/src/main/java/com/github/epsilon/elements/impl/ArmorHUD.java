package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.awt.*;
import java.util.function.Supplier;

public class ArmorHUD extends HudModule {

    public static final ArmorHUD INSTANCE = new ArmorHUD();

    private enum Orientation {
        Horizontal,
        Vertical
    }

    private enum DurabilityMode {
        Bar,
        Remaining,
        Percentage,
        Hidden
    }

    private enum HorizontalAlignment {
        Left,
        Center,
        Right
    }

    private final EnumSetting<Orientation> orientation = enumSetting("Orientation", Orientation.Horizontal);
    private final BoolSetting flipOrder = boolSetting("Flip Order", true);
    private final BoolSetting showEmpty = boolSetting("Show Empty", false);
    private final EnumSetting<HorizontalAlignment> alignment = enumSetting("Alignment", HorizontalAlignment.Left,
            () -> orientation.is(Orientation.Vertical));
    private final EnumSetting<DurabilityMode> durabilityMode = enumSetting("Durability Mode", DurabilityMode.Bar,
            () -> orientation.is(Orientation.Horizontal));
    private final DoubleSetting scale = doubleSetting("Scale", 1.5, 0.5, 3.0, 0.1);
    private final DoubleSetting gap = doubleSetting("Gap", 2.0, 0.0, 8.0, 0.5);
    private final BoolSetting background = boolSetting("Background", false);
    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(15, 15, 15, 130), background::getValue);
    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);

    private ArmorHUD() {
        super("Armor HUD", 0f, 0f, 108f, 24f);
    }

    @Override
    protected boolean shouldRenderTextShadow() {
        return !background.getValue();
    }

    @Override
    public void render(DeltaTracker deltaTracker) {
        float s = scale.getValue().floatValue();
        float itemSize = 16f * s;
        float spacing = gap.getValue().floatValue() * s;
        float textScale = 0.56f * s;
        float detailGap = 4f * s;
        float detailWidth = orientation.is(Orientation.Vertical) ? maxDetailWidth(textScale) : 0f;
        float width = orientation.is(Orientation.Horizontal)
                ? itemSize * 4f + spacing * 3f
                : itemSize + (detailWidth > 0f ? detailGap + detailWidth : 0f);
        float height = orientation.is(Orientation.Vertical) ? itemSize * 4f + spacing * 3f : itemSize;
        setBounds(width, height);
        if (background.getValue()) renderScope().roundRect(this.x - 2f, this.y - 2f, width + 4f, height + 4f, 3f, backgroundColor.getValue());

        if (orientation.is(Orientation.Vertical) && mc.player != null) {
            TextRenderer renderer = textRendererSupplier.get();
            EquipmentSlot[] slots = equipmentSlots();
            float step = itemSize + spacing;
            for (int i = 0; i < slots.length; i++) {
                ItemStack stack = mc.player.getItemBySlot(slots[i]);
                if (stack.isEmpty()) continue;
                String detail = detailedDurability(stack);
                if (detail == null) continue;
                float rowWidth = itemSize + detailGap + renderer.getWidth(detail, textScale);
                float rowX = alignedX(width, rowWidth);
                float textY = this.y + i * step + (itemSize - renderer.getHeight(textScale)) / 2f;
                renderScope().text(detail, rowX + itemSize + detailGap, textY, textScale, durabilityColor(stack));
            }
        }
    }

    @Override
    public void renderOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (nullCheck()) return;

        EquipmentSlot[] slots = equipmentSlots();
        float s = scale.getValue().floatValue();
        float step = (16f + gap.getValue().floatValue()) * s;
        float textScale = 0.56f * s;
        float detailGap = 4f * s;
        float detailWidth = orientation.is(Orientation.Vertical) ? maxDetailWidth(textScale) : 0f;
        float width = orientation.is(Orientation.Horizontal)
                ? 16f * s * 4f + gap.getValue().floatValue() * s * 3f
                : 16f * s + (detailWidth > 0f ? detailGap + detailWidth : 0f);
        TextRenderer renderer = orientation.is(Orientation.Vertical) ? textRendererSupplier.get() : null;

        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = mc.player.getItemBySlot(slots[i]);
            if (stack.isEmpty() && showEmpty.getValue()) stack = Items.BARRIER.getDefaultInstance();
            if (stack.isEmpty()) continue;
            float itemX;
            if (orientation.is(Orientation.Horizontal)) {
                itemX = this.x + i * step;
            } else {
                String detail = detailedDurability(stack);
                float rowWidth = 16f * s + detailGap + renderer.getWidth(detail, textScale);
                itemX = alignedX(width, rowWidth);
            }
            float itemY = this.y + (orientation.is(Orientation.Vertical) ? i * step : 0f);
            drawItem(graphics, stack, itemX, itemY, s);
        }
    }

    private void drawItem(GuiGraphicsExtractor graphics, ItemStack stack, float x, float y, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.item(stack, 0, 0);
        String text = orientation.is(Orientation.Horizontal) ? durabilityText(stack) : null;
        boolean showDurabilityBar = orientation.is(Orientation.Vertical)
                ? stack.isDamageableItem()
                : stack.isDamageableItem() && !durabilityMode.is(DurabilityMode.Hidden);
        if (showDurabilityBar) {
            graphics.itemDecorations(mc.font, stack, 0, 0, text);
        }
        graphics.pose().popMatrix();
    }

    private float alignedX(float panelWidth, float rowWidth) {
        float available = panelWidth - rowWidth;
        return this.x + switch (alignment.getValue()) {
            case Left -> 0f;
            case Center -> available / 2f;
            case Right -> available;
        };
    }

    private String durabilityText(ItemStack stack) {
        if (!stack.isDamageableItem()) return null;
        int remaining = stack.getMaxDamage() - stack.getDamageValue();
        return switch (durabilityMode.getValue()) {
            case Bar -> null;
            case Remaining -> Integer.toString(remaining);
            case Percentage -> Math.round(remaining * 100f / stack.getMaxDamage()) + "%";
            case Hidden -> null;
        };
    }

    private EquipmentSlot[] equipmentSlots() {
        return flipOrder.getValue()
                ? new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}
                : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    }

    private float maxDetailWidth(float textScale) {
        if (mc.player == null) return 0f;
        TextRenderer renderer = textRendererSupplier.get();
        float width = 0f;
        for (EquipmentSlot slot : equipmentSlots()) {
            ItemStack stack = mc.player.getItemBySlot(slot);
            if (stack.isEmpty() && showEmpty.getValue()) stack = Items.BARRIER.getDefaultInstance();
            String detail = maxDetailedDurability(stack);
            if (detail != null) width = Math.max(width, renderer.getWidth(detail, textScale));
        }
        return width;
    }

    private String detailedDurability(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) return "N/A";
        int remaining = stack.getMaxDamage() - stack.getDamageValue();
        int percentage = Math.round(remaining * 100f / stack.getMaxDamage());
        return remaining + " / " + stack.getMaxDamage() + " (" + percentage + "%)";
    }

    // 满耐久文字（用于面板宽度计算，保证耐久变化时面板不 resize）
    private String maxDetailedDurability(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) return "N/A";
        int max = stack.getMaxDamage();
        return max + " / " + max + " (100%)";
    }

    private Color durabilityColor(ItemStack stack) {
        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) return new Color(170, 180, 195, 235);
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
}
