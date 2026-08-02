package com.github.epsilon.modules.impl.render.bettertooltips;

import com.github.epsilon.utils.player.ContainerItemUtils;
import com.github.epsilon.utils.client.KeybindUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.ScrollWheelHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import org.lwjgl.glfw.GLFW;
import org.joml.Vector2i;

import java.util.ArrayList;
import java.util.List;

import static com.github.epsilon.Constants.mc;

public class ContainerInventoryScreen extends Screen {
    private static final Identifier SLOT_TEXTURE = Identifier.withDefaultNamespace("container/slot");
    private static final int SLOT_SIZE = 18;
    private static final int SCREEN_WIDTH = 176;

    private final List<ItemStack> containerItems = new ArrayList<>();
    private final Inventory playerInventory;
    private final ItemStack initialContainerItem;
    private final ItemStack sourceItem;
    private final AbstractContainerMenu sourceMenu;
    private ItemStack trackedContainerItem;
    private boolean sourceInCarried;
    private int sourceSlotId;
    private final Screen parentScreen;
    private final ScrollWheelHandler scrollWheelHandler = new ScrollWheelHandler();
    private int containerRows;
    private int containerColumns;
    private int x;
    private int y;
    private int baseX;
    private int containerBaseX;
    private int baseY;
    private int playerY;

    public ContainerInventoryScreen(ItemStack containerItem, AbstractContainerMenu sourceMenu, int sourceSlotId, Screen parentScreen) {
        super(containerItem.getHoverName());
        playerInventory = mc.player.getInventory();
        initialContainerItem = containerItem.copy();
        sourceItem = containerItem;
        trackedContainerItem = initialContainerItem.copy();
        this.sourceMenu = sourceMenu;
        this.sourceSlotId = sourceSlotId;
        this.parentScreen = parentScreen;
        refreshContainerItems();
    }

    public ContainerInventoryScreen(ItemStack containerItem) {
        this(containerItem, null, -1, null);
    }

    @Override
    protected void init() {
        super.init();
        updateLayout();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        syncContainerItems();
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        updateCoordinates();
        baseX = x + 8;
        baseY = y + 18;
        playerY = baseY + containerRows * SLOT_SIZE + 20;

        for (int row = 0; row < containerRows + 4; row++) {
            int columns = row < containerRows ? containerColumns : 9;
            for (int col = 0; col < columns; col++) {
                int slotY = row < containerRows ? baseY + row * SLOT_SIZE : playerY + (row - containerRows) * SLOT_SIZE;
                int slotX = row < containerRows ? containerBaseX + col * SLOT_SIZE : baseX + col * SLOT_SIZE;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, slotX, slotY, SLOT_SIZE, SLOT_SIZE);
            }
        }
        int containerSlotCount = containerRows * containerColumns;
        for (int i = 0; i < containerItems.size() && i < containerSlotCount; i++) {
            ItemStack item = containerItems.get(i);
            if (!item.isEmpty()) {
                int itemX = containerBaseX + (i % containerColumns) * SLOT_SIZE + 1;
                int itemY = baseY + (i / containerColumns) * SLOT_SIZE + 1;
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

        ItemStack carried = getCarriedItem();
        if (!carried.isEmpty()) {
            graphics.nextStratum();
            graphics.item(carried, mouseX - 8, mouseY - 8);
            graphics.itemDecorations(font, carried, mouseX - 8, mouseY - 8);
        }

        ItemStack item = getSelectedItem(mouseX, mouseY);
        if (!item.isEmpty() && carried.isEmpty()) graphics.setTooltipForNextFrame(font, item, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        syncContainerItems();
        int containerGridIndex = getContainerGridIndex((int) event.x(), (int) event.y());
        if ((event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT || event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                && putBundleItem(containerGridIndex)) {
            return true;
        }
        if ((event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT || event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                && takeBundleItem(containerGridIndex)) {
            return true;
        }
        if (movePlayerInventoryItem(event)) return true;
        if (BetterTooltips.INSTANCE.shouldOpenContents(KeybindUtils.encodeMouseButton(event.button()), true)) {
            return openSelectedContent((int) event.x(), (int) event.y());
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        syncContainerItems();
        AbstractContainerMenu menu = getActiveMenu();
        if (menu == null || mc.getConnection() == null || mouseX < baseX || mouseX >= baseX + 9 * SLOT_SIZE
                || mouseY < playerY || mouseY >= playerY + 4 * SLOT_SIZE) return false;

        int col = ((int) mouseX - baseX) / SLOT_SIZE;
        int row = ((int) mouseY - playerY) / SLOT_SIZE;
        int inventoryIndex = row < 3 ? 9 + row * 9 + col : col;
        int menuSlot = findPlayerInventoryMenuSlot(menu, inventoryIndex);
        if (menuSlot < 0 || menuSlot >= menu.slots.size()) return false;

        Slot slot = menu.getSlot(menuSlot);
        ItemStack bundle = slot.getItem();
        if (!(bundle.getItem() instanceof BundleItem)) return false;

        int shownItems = BundleItem.getNumberOfItemsToShow(bundle);
        if (shownItems == 0) return false;
        Vector2i wheelXY = scrollWheelHandler.onMouseScroll(scrollX, scrollY);
        int wheel = wheelXY.y == 0 ? -wheelXY.x : wheelXY.y;
        if (wheel == 0) return true;

        int selected = BundleItem.getSelectedItemIndex(bundle);
        int updated = ScrollWheelHandler.getNextScrollWheelSelection(wheel, selected, shownItems);
        if (selected != updated) {
            int currentSourceSlot = resolveSourceSlot(menu);
            BundleItem.toggleSelectedItem(bundle, updated);
            mc.getConnection().send(new ServerboundSelectBundleItemPacket(menuSlot, updated));
            if (menuSlot == currentSourceSlot) rememberSourceItem(slot);
            syncContainerItems();
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE || mc.options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        if (BetterTooltips.INSTANCE.shouldOpenContents(event.key(), false)) {
            return openSelectedContent((int) mc.mouseHandler.getScaledXPos(mc.getWindow()), (int) mc.mouseHandler.getScaledYPos(mc.getWindow()));
        }
        return super.keyPressed(event);
    }

    private boolean openSelectedContent(int mouseX, int mouseY) {
        ItemStack selected = getSelectedItem(mouseX, mouseY);
        if (selected.isEmpty()) return false;
        AbstractContainerMenu menu = getActiveMenu();
        int inventoryIndex = getPlayerInventoryIndex(mouseX, mouseY);
        if (menu != null && inventoryIndex >= 0) {
            int menuSlot = findPlayerInventoryMenuSlot(menu, inventoryIndex);
            if (menuSlot >= 0) return BetterTooltips.INSTANCE.openContent(selected, menu, menuSlot, this);
        }
        return BetterTooltips.INSTANCE.openContent(selected);
    }

    private ItemStack getSelectedItem(int mouseX, int mouseY) {
        int index = getContainerItemIndex(mouseX, mouseY);
        if (index >= 0) return containerItems.get(index);
        if (mouseY >= playerY && mouseY < playerY + 4 * SLOT_SIZE) {
            if (mouseX < baseX || mouseX >= baseX + 9 * SLOT_SIZE) return ItemStack.EMPTY;
            int col = (mouseX - baseX) / SLOT_SIZE;
            int row = (mouseY - playerY) / SLOT_SIZE;
            return playerInventory.getItem(row < 3 ? 9 + row * 9 + col : col);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (parentScreen != null && mc.screen == this) mc.setScreen(parentScreen);
        else super.onClose();
    }

    private int getContainerItemIndex(int mouseX, int mouseY) {
        int index = getContainerGridIndex(mouseX, mouseY);
        return index >= 0 && index < containerItems.size() ? index : -1;
    }

    private int getContainerGridIndex(int mouseX, int mouseY) {
        if (mouseX < containerBaseX || mouseX >= containerBaseX + containerColumns * SLOT_SIZE
                || mouseY < baseY || mouseY >= baseY + containerRows * SLOT_SIZE) {
            return -1;
        }
        return (mouseY - baseY) / SLOT_SIZE * containerColumns + (mouseX - containerBaseX) / SLOT_SIZE;
    }

    private boolean putBundleItem(int index) {
        AbstractContainerMenu menu = getActiveMenu();
        int slotId = resolveSourceSlot(menu);
        if (index < 0 || menu == null || slotId < 0 || sourceInCarried || mc.player == null || mc.gameMode == null || menu.getCarried().isEmpty()) return false;

        ItemStack bundle = menu.getSlot(slotId).getItem();
        if (!(bundle.getItem() instanceof BundleItem)) return false;

        mc.gameMode.handleContainerInput(menu.containerId, slotId, 0, ContainerInput.PICKUP, mc.player);
        rememberSourceItem(menu.getSlot(slotId));
        refreshContainerItems();
        updateLayout();
        return true;
    }

    private boolean takeBundleItem(int index) {
        AbstractContainerMenu menu = getActiveMenu();
        int slotId = resolveSourceSlot(menu);
        if (index < 0 || menu == null || slotId < 0 || mc.player == null || mc.gameMode == null || mc.getConnection() == null
                || !menu.getCarried().isEmpty()) return false;

        Slot sourceSlot = menu.getSlot(slotId);
        ItemStack bundle = sourceSlot.getItem();
        if (!(bundle.getItem() instanceof BundleItem)) return false;

        BundleContents contents = bundle.get(DataComponents.BUNDLE_CONTENTS);
        if (contents == null || index >= contents.size()) return false;

        BundleItem.toggleSelectedItem(bundle, index);
        sourceSlotId = slotId;
        mc.getConnection().send(new ServerboundSelectBundleItemPacket(slotId, index));
        mc.gameMode.handleContainerInput(menu.containerId, slotId, 1, ContainerInput.PICKUP, mc.player);
        rememberSourceItem(sourceSlot);
        refreshContainerItems();
        updateLayout();
        return true;
    }

    private boolean movePlayerInventoryItem(MouseButtonEvent event) {
        AbstractContainerMenu menu = getActiveMenu();
        if (sourceMenu == null || menu == null || mc.player == null || mc.gameMode == null
                || (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT && event.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT)) return false;
        if (event.x() < baseX || event.x() >= baseX + 9 * SLOT_SIZE || event.y() < playerY || event.y() >= playerY + 4 * SLOT_SIZE) return false;

        int inventoryIndex = getPlayerInventoryIndex((int) event.x(), (int) event.y());
        if (inventoryIndex < 0) return false;
        int menuSlot = findPlayerInventoryMenuSlot(menu, inventoryIndex);
        if (menuSlot < 0 || menuSlot >= menu.slots.size()) return false;

        int currentSourceSlot = resolveSourceSlot(menu);
        ItemStack carriedBefore = menu.getCarried();
        ItemStack clickedBefore = menu.getSlot(menuSlot).getItem();
        boolean pickingUpSource = !sourceInCarried && carriedBefore.isEmpty() && menuSlot == currentSourceSlot
                && isTrackedBundle(clickedBefore);
        boolean placingSource = sourceInCarried && !carriedBefore.isEmpty() && carriedBefore.getItem() instanceof BundleItem;

        ContainerInput input = event.hasShiftDown() && menu.getCarried().isEmpty()
                ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP;
        mc.gameMode.handleContainerInput(menu.containerId, menuSlot, event.button(), input, mc.player);
        if (menuSlot == currentSourceSlot) rememberSourceItem(menu.getSlot(menuSlot));
        if (pickingUpSource && !menu.getCarried().isEmpty() && isTrackedBundle(menu.getCarried())) {
            sourceInCarried = true;
        } else if (placingSource && isTrackedBundle(menu.getSlot(menuSlot).getItem())) {
            sourceSlotId = menuSlot;
            sourceInCarried = false;
        }
        refreshContainerItems();
        updateLayout();
        return true;
    }

    private int findPlayerInventoryMenuSlot(AbstractContainerMenu menu, int inventoryIndex) {
        for (Slot slot : menu.slots) {
            if (slot.container == playerInventory && slot.getContainerSlot() == inventoryIndex) return slot.index;
        }
        return -1;
    }

    private int getPlayerInventoryIndex(int mouseX, int mouseY) {
        if (mouseX < baseX || mouseX >= baseX + 9 * SLOT_SIZE || mouseY < playerY || mouseY >= playerY + 4 * SLOT_SIZE) return -1;
        int col = (mouseX - baseX) / SLOT_SIZE;
        int row = (mouseY - playerY) / SLOT_SIZE;
        return row < 3 ? 9 + row * 9 + col : col;
    }

    private ItemStack getCarriedItem() {
        AbstractContainerMenu menu = getActiveMenu();
        return sourceMenu == null || menu == null ? ItemStack.EMPTY : menu.getCarried();
    }

    private void refreshContainerItems() {
        containerItems.clear();
        AbstractContainerMenu menu = getActiveMenu();
        int slotId = resolveSourceSlot(menu);
        boolean hasLiveSource = menu != null && slotId >= 0;
        ItemStack containerItem = hasLiveSource ? menu.getSlot(slotId).getItem() : ItemStack.EMPTY;
        if (containerItem.isEmpty() && sourceInCarried && menu != null && menu.getCarried().getItem() instanceof BundleItem) {
            containerItem = menu.getCarried();
            hasLiveSource = true;
        }
        if (containerItem.isEmpty()) containerItem = trackedContainerItem;
        if (containerItem.isEmpty()) containerItem = initialContainerItem;
        if (hasLiveSource && !containerItem.isEmpty()) trackedContainerItem = containerItem.copy();

        if (containerItem.getItem() instanceof BundleItem) {
            BundleContents contents = containerItem.get(DataComponents.BUNDLE_CONTENTS);
            if (contents != null) {
                contents.items().stream()
                        .map(ContainerItemUtils::copyTemplateForDisplay)
                        .forEach(containerItems::add);
            }
            containerColumns = 9;
            containerRows = Math.max(1, Mth.positiveCeilDiv(containerItems.size(), 9));
        } else {
            containerColumns = ContainerItemUtils.isThreeByThreeContainer(containerItem) ? 3 : 9;
            ItemStack[] items = new ItemStack[containerColumns * 3];
            ContainerItemUtils.copyItems(containerItem, items);
            for (ItemStack item : items) containerItems.add(item);
            containerRows = 3;
        }
    }

    private void rememberSourceItem(Slot slot) {
        ItemStack item = slot.getItem();
        if (!item.isEmpty()) trackedContainerItem = item.copy();
    }

    private void syncContainerItems() {
        int previousRows = containerRows;
        int previousColumns = containerColumns;
        refreshContainerItems();
        if (previousRows != containerRows || previousColumns != containerColumns) updateLayout();
    }

    private void updateLayout() {
        if (width <= 0 || height <= 0) return;
        x = (width - SCREEN_WIDTH) / 2;
        y = (height - (114 + containerRows * SLOT_SIZE + 20)) / 2;
        updateCoordinates();
    }

    private void updateCoordinates() {
        baseX = x + 8;
        containerBaseX = x + (SCREEN_WIDTH - containerColumns * SLOT_SIZE) / 2;
        baseY = y + 18;
        playerY = baseY + containerRows * SLOT_SIZE + 20;
    }

    private AbstractContainerMenu getActiveMenu() {
        return sourceMenu == null || mc.player == null ? null : mc.player.containerMenu;
    }

    private int resolveSourceSlot(AbstractContainerMenu menu) {
        if (menu == null || sourceInCarried) return -1;
        if (sourceSlotId >= 0 && sourceSlotId < menu.slots.size()) {
            Slot slot = menu.getSlot(sourceSlotId);
            ItemStack item = slot.getItem();
            if (!item.isEmpty() && (item == sourceItem
                    || sameContainerItem(item, trackedContainerItem)
                    || sameContainerItem(item, initialContainerItem))) return sourceSlotId;
        }
        for (Slot slot : menu.slots) {
            if (!slot.getItem().isEmpty() && slot.getItem() == sourceItem) {
                sourceSlotId = slot.index;
                return sourceSlotId;
            }
        }
        for (Slot slot : menu.slots) {
            ItemStack item = slot.getItem();
            if (!item.isEmpty() && sameContainerItem(item, trackedContainerItem)) {
                sourceSlotId = slot.index;
                return sourceSlotId;
            }
        }
        for (Slot slot : menu.slots) {
            ItemStack item = slot.getItem();
            if (!item.isEmpty() && sameContainerItem(item, initialContainerItem)) {
                sourceSlotId = slot.index;
                return sourceSlotId;
            }
        }
        return -1;
    }

    private boolean isTrackedBundle(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BundleItem
                && sameContainerItem(stack, trackedContainerItem);
    }

    private boolean sameContainerItem(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty() || first.getItem() != second.getItem()) return false;
        if (first.getItem() instanceof BundleItem) {
            BundleContents firstContents = first.get(DataComponents.BUNDLE_CONTENTS);
            BundleContents secondContents = second.get(DataComponents.BUNDLE_CONTENTS);
            return firstContents != null && secondContents != null && firstContents.items().equals(secondContents.items());
        }
        return ItemStack.isSameItemSameComponents(first, second);
    }
}
