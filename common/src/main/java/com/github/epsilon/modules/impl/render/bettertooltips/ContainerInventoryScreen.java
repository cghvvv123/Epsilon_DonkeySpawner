package com.github.epsilon.modules.impl.render.bettertooltips;

import com.github.epsilon.utils.player.ContainerItemUtils;
import com.github.epsilon.utils.client.KeybindUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.github.epsilon.Constants.mc;

public class ContainerInventoryScreen extends Screen {
    private static final Identifier SLOT_TEXTURE = Identifier.withDefaultNamespace("container/slot");
    private static final int SLOT_SIZE = 18;
    private static final int SCREEN_WIDTH = 176;

    private final List<ItemStack> containerItems = new ArrayList<>();
    private final Inventory playerInventory;
    private final int containerRows;
    private int x;
    private int y;
    private int baseX;
    private int baseY;
    private int playerY;

    public ContainerInventoryScreen(ItemStack containerItem) {
        super(containerItem.getHoverName());
        playerInventory = mc.player.getInventory();
        if (containerItem.getItem() instanceof BundleItem) {
            BundleContents contents = containerItem.get(DataComponents.BUNDLE_CONTENTS);
            if (contents != null) contents.items().forEach(template -> containerItems.add(template.create()));
        } else {
            ItemStack[] items = new ItemStack[64];
            ContainerItemUtils.copyItems(containerItem, items);
            for (ItemStack item : items) containerItems.add(item);
        }
        containerRows = Math.max(1, Mth.positiveCeilDiv(containerItems.size(), 9));
    }

    @Override
    protected void init() {
        super.init();
        x = (width - SCREEN_WIDTH) / 2;
        y = (height - (114 + containerRows * SLOT_SIZE + 20)) / 2;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        baseX = x + 8;
        baseY = y + 18;
        playerY = baseY + containerRows * SLOT_SIZE + 20;

        for (int row = 0; row < containerRows + 4; row++) {
            for (int col = 0; col < 9; col++) {
                int slotY = row < containerRows ? baseY + row * SLOT_SIZE : playerY + (row - containerRows) * SLOT_SIZE;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, baseX + col * SLOT_SIZE, slotY, SLOT_SIZE, SLOT_SIZE);
            }
        }
        for (int i = 0; i < containerItems.size(); i++) {
            ItemStack item = containerItems.get(i);
            if (!item.isEmpty()) {
                int itemX = baseX + (i % 9) * SLOT_SIZE + 1;
                int itemY = baseY + (i / 9) * SLOT_SIZE + 1;
                graphics.item(item, itemX, itemY);
                graphics.itemDecorations(font, item, itemX, itemY);
            }
        }
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int index = row < 3 ? 9 + row * 9 + col : col;
                ItemStack item = playerInventory.getItem(index);
                if (!item.isEmpty()) {
                    int itemX = baseX + col * SLOT_SIZE + 1;
                    int itemY = playerY + row * SLOT_SIZE + 1;
                    graphics.item(item, itemX, itemY);
                    graphics.itemDecorations(font, item, itemX, itemY);
                }
            }
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.text(font, title, 8, 6, -12566464, false);
        graphics.text(font, playerInventory.getDisplayName(), 8, 18 + containerRows * SLOT_SIZE + 10, -12566464, false);
        graphics.pose().popMatrix();

        ItemStack item = getSelectedItem(mouseX, mouseY);
        if (!item.isEmpty()) graphics.setTooltipForNextFrame(font, item, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (BetterTooltips.INSTANCE.shouldOpenContents(KeybindUtils.encodeMouseButton(event.button()), true)) return BetterTooltips.INSTANCE.openContent(getSelectedItem((int) event.x(), (int) event.y()));
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE || mc.options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        if (BetterTooltips.INSTANCE.shouldOpenContents(event.key(), false)) return BetterTooltips.INSTANCE.openContent(getSelectedItem((int) mc.mouseHandler.getScaledXPos(mc.getWindow()), (int) mc.mouseHandler.getScaledYPos(mc.getWindow())));
        return super.keyPressed(event);
    }

    private ItemStack getSelectedItem(int mouseX, int mouseY) {
        if (mouseX < baseX || mouseX >= baseX + 9 * SLOT_SIZE) return ItemStack.EMPTY;
        int col = (mouseX - baseX) / SLOT_SIZE;
        if (mouseY >= baseY && mouseY < baseY + containerRows * SLOT_SIZE) {
            int index = (mouseY - baseY) / SLOT_SIZE * 9 + col;
            return index < containerItems.size() ? containerItems.get(index) : ItemStack.EMPTY;
        }
        if (mouseY >= playerY && mouseY < playerY + 4 * SLOT_SIZE) {
            int row = (mouseY - playerY) / SLOT_SIZE;
            return playerInventory.getItem(row < 3 ? 9 + row * 9 + col : col);
        }
        return ItemStack.EMPTY;
    }
}
