package com.github.epsilon.utils.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.GameLeftEvent;
import com.github.epsilon.events.impl.LevelUpdateEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.mojang.serialization.DataResult;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.awt.*;
import java.util.Arrays;

import static com.github.epsilon.Constants.mc;

public final class ContainerItemUtils {

    private static final int INVENTORY_SIZE = 27;
    private static final NonNullList<ItemStack> ENDER_CHEST_ITEMS = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private static boolean enderChestKnown;

    private ContainerItemUtils() {
    }

    @EventHandler
    private static void onTick(PlayerTickEvent.Pre event) {
        if (mc.player == null || !(mc.screen instanceof AbstractContainerScreen<?> screen)
                || !(screen.getMenu() instanceof ChestMenu menu)) return;
        if (screen.getTitle().getString().equals(Component.translatable("container.enderchest").getString())) {
            rememberEnderChest(menu);
        }
    }

    @EventHandler
    private static void onLevelUpdate(LevelUpdateEvent event) {
        clearEnderChest();
    }

    @EventHandler
    private static void onGameLeft(GameLeftEvent event) {
        clearEnderChest();
    }

    public static ItemStack findHeldContainer() {
        ItemStack mainHand = mc.player.getMainHandItem();
        if (isContainer(mainHand)) return mainHand;

        ItemStack offHand = mc.player.getOffhandItem();
        return isContainer(offHand) ? offHand : ItemStack.EMPTY;
    }

    public static boolean isContainer(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.is(Items.BUNDLE)) return false;
        if (stack.is(Items.ENDER_CHEST)) return enderChestKnown;
        return stack.has(DataComponents.CONTAINER) || hasBlockEntityItems(stack);
    }

    public static void copyItems(ItemStack stack, ItemStack[] output) {
        Arrays.fill(output, ItemStack.EMPTY);
        if (stack.is(Items.ENDER_CHEST)) {
            if (!enderChestKnown) return;
            for (int i = 0; i < ENDER_CHEST_ITEMS.size() && i < output.length; i++) {
                output[i] = ENDER_CHEST_ITEMS.get(i);
            }
            return;
        }

        var container = stack.get(DataComponents.CONTAINER);
        if (container != null) {
            var items = container.allItemsCopyStream().toList();
            for (int i = 0; i < items.size() && i < output.length; i++) {
                output[i] = items.get(i);
            }
            return;
        }

        TypedEntityData<BlockEntityType<?>> blockEntityData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (blockEntityData == null || mc.player == null) return;
        ListTag itemTags = blockEntityData.copyTagWithoutId().getListOrEmpty("Items");
        for (int i = 0; i < itemTags.size(); i++) {
            CompoundTag itemTag = itemTags.getCompound(i).orElse(null);
            if (itemTag == null) continue;
            int slot = itemTag.getByte("Slot").orElse((byte) -1);
            if (slot < 0 || slot >= output.length) continue;
            DataResult<ItemStackWithSlot> result = ItemStackWithSlot.CODEC.parse(
                    mc.player.registryAccess().createSerializationContext(NbtOps.INSTANCE), itemTag
            );
            result.result().ifPresent(value -> output[slot] = value.stack());
        }
    }

    public static Color backgroundColor(ItemStack stack) {
        if (stack.is(Items.ENDER_CHEST)) return new Color(74, 48, 112, 220);
        if (stack.getItem() instanceof BlockItem blockItem) {
            if (blockItem.getBlock() == Blocks.CHEST) return new Color(156, 102, 54, 220);
            if (blockItem.getBlock() instanceof ShulkerBoxBlock shulkerBox) {
                if (shulkerBox.getColor() == null) return new Color(135, 135, 135, 220);
                int color = shulkerBox.getColor().getTextureDiffuseColor();
                return new Color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 220);
            }
        }
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        int hash = key == null ? 0 : key.hashCode();
        Color generated = Color.getHSBColor(Math.floorMod(hash, 360) / 360f, 0.35f, 0.65f);
        return new Color(generated.getRed(), generated.getGreen(), generated.getBlue(), 220);
    }

    public static void rememberEnderChest(ChestMenu menu) {
        for (int i = 0; i < INVENTORY_SIZE && i < menu.getContainer().getContainerSize(); i++) {
            ENDER_CHEST_ITEMS.set(i, menu.getContainer().getItem(i).copy());
        }
        enderChestKnown = true;
    }

    public static void clearEnderChest() {
        for (int i = 0; i < ENDER_CHEST_ITEMS.size(); i++) {
            ENDER_CHEST_ITEMS.set(i, ItemStack.EMPTY);
        }
        enderChestKnown = false;
    }

    private static boolean hasBlockEntityItems(ItemStack stack) {
        TypedEntityData<BlockEntityType<?>> data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        return data != null && data.contains("Items");
    }
}
