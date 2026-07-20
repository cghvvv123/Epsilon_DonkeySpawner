package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.RegistryListSetting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.awt.*;
import java.util.List;

public class ItemCounterHUD extends HudModule {

    public static final ItemCounterHUD INSTANCE = new ItemCounterHUD();

    private final RegistryListSetting<Item> items = itemListSetting("Items", List.of(Items.TOTEM_OF_UNDYING));
    private final DoubleSetting scale = doubleSetting("Scale", 1.5, 0.5, 3.0, 0.1);
    private final DoubleSetting gap = doubleSetting("Gap", 2.0, 0.0, 8.0, 0.5);
    private final BoolSetting hideMissing = boolSetting("Hide Missing", false);
    private final BoolSetting background = boolSetting("Background", false);
    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(15, 15, 15, 130), background::getValue);

    private ItemCounterHUD() {
        super("Item Counter HUD", 0f, 0f, 28f, 28f);
    }

    @Override
    public void render(DeltaTracker deltaTracker) {
        float s = scale.getValue().floatValue();
        int visible = visibleItems();
        float width = Math.max(16f * s, visible * 16f * s + Math.max(0, visible - 1) * gap.getValue().floatValue() * s);
        float height = 16f * s;
        setBounds(width, height);
        if (background.getValue()) renderScope().roundRect(this.x - 2f, this.y - 2f, width + 4f, height + 4f, 3f, backgroundColor.getValue());
    }

    @Override
    public void renderOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (nullCheck()) return;

        float s = scale.getValue().floatValue();
        float step = (16f + gap.getValue().floatValue()) * s;
        int index = 0;
        for (Item item : items.getValue()) {
            int count = count(item);
            if (hideMissing.getValue() && count == 0) continue;
            ItemStack stack = item.getDefaultInstance();
            stack.setCount(Math.max(1, count));

            graphics.pose().pushMatrix();
            graphics.pose().translate(this.x + index * step, this.y);
            graphics.pose().scale(s, s);
            graphics.item(stack, 0, 0);
            graphics.itemDecorations(mc.font, stack, 0, 0, Integer.toString(count));
            graphics.pose().popMatrix();
            index++;
        }
    }

    private int visibleItems() {
        if (nullCheck() || !hideMissing.getValue()) return Math.max(1, items.size());
        int visible = 0;
        for (Item item : items.getValue()) if (count(item) > 0) visible++;
        return Math.max(1, visible);
    }

    private int count(Item item) {
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }
}
