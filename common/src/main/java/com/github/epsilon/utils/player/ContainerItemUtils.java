package com.github.epsilon.utils.player;

import com.github.epsilon.interfaces.ItemContainerContentsAccessor;
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
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;

import java.awt.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.world.item.DyeColor;

import static com.github.epsilon.Constants.mc;

public final class ContainerItemUtils {

    private static final int INVENTORY_SIZE = 27;
    private static final NonNullList<ItemStack> ENDER_CHEST_ITEMS = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private static boolean enderChestKnown;

    // 彩色收纳袋是各自独立的物品（颜色烤进贴图，不带 DYED_COLOR 组件），需按物品映射到对应 DyeColor 取色，
    // 与潜影盒用 DyeColor.getTextureDiffuseColor() 取色同思路。基础收纳袋回落到默认皮革色。
    private static final Map<net.minecraft.world.item.Item, DyeColor> BUNDLE_COLORS = new HashMap<>();
    static {
        BUNDLE_COLORS.put(Items.WHITE_BUNDLE, DyeColor.WHITE);
        BUNDLE_COLORS.put(Items.ORANGE_BUNDLE, DyeColor.ORANGE);
        BUNDLE_COLORS.put(Items.MAGENTA_BUNDLE, DyeColor.MAGENTA);
        BUNDLE_COLORS.put(Items.LIGHT_BLUE_BUNDLE, DyeColor.LIGHT_BLUE);
        BUNDLE_COLORS.put(Items.YELLOW_BUNDLE, DyeColor.YELLOW);
        BUNDLE_COLORS.put(Items.LIME_BUNDLE, DyeColor.LIME);
        BUNDLE_COLORS.put(Items.PINK_BUNDLE, DyeColor.PINK);
        BUNDLE_COLORS.put(Items.GRAY_BUNDLE, DyeColor.GRAY);
        BUNDLE_COLORS.put(Items.LIGHT_GRAY_BUNDLE, DyeColor.LIGHT_GRAY);
        BUNDLE_COLORS.put(Items.CYAN_BUNDLE, DyeColor.CYAN);
        BUNDLE_COLORS.put(Items.PURPLE_BUNDLE, DyeColor.PURPLE);
        BUNDLE_COLORS.put(Items.BLUE_BUNDLE, DyeColor.BLUE);
        BUNDLE_COLORS.put(Items.BROWN_BUNDLE, DyeColor.BROWN);
        BUNDLE_COLORS.put(Items.GREEN_BUNDLE, DyeColor.GREEN);
        BUNDLE_COLORS.put(Items.RED_BUNDLE, DyeColor.RED);
        BUNDLE_COLORS.put(Items.BLACK_BUNDLE, DyeColor.BLACK);
    }

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
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.ENDER_CHEST)) return enderChestKnown;
        return stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.BUNDLE_CONTENTS) || hasBlockEntityItems(stack);
    }

    public static boolean isBundle(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.has(DataComponents.BUNDLE_CONTENTS);
    }

    public static boolean isThreeByThreeContainer(ItemStack stack) {
        return stack != null && (stack.is(Items.DISPENSER) || stack.is(Items.DROPPER));
    }

    public static ItemStack copyTemplateForDisplay(ItemStackTemplate template) {
        return new ItemStack(template.item(), template.count(), template.components());
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

        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null) {
            List<Optional<ItemStackTemplate>> items = ((ItemContainerContentsAccessor) (Object) container).epsilon$getItems();
            for (int i = 0; i < items.size() && i < output.length; i++) {
                Optional<ItemStackTemplate> item = items.get(i);
                if (item.isPresent()) output[i] = copyTemplateForDisplay(item.get());
            }
            return;
        }

        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            var items = bundle.items();
            for (int i = 0; i < items.size() && i < output.length; i++) {
                output[i] = copyTemplateForDisplay(items.get(i));
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

    public static int getItemCount(ItemStack stack) {
        if (isBundle(stack)) {
            BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
            return bundle == null ? 0 : bundle.size();
        }
        if (isThreeByThreeContainer(stack)) return 9;
        return INVENTORY_SIZE;
    }

    public static Color backgroundColor(ItemStack stack) {
        if (stack.is(Items.ENDER_CHEST)) return new Color(74, 48, 112, 220);
        if (stack.getItem() instanceof BlockItem blockItem) {
            if (blockItem.getBlock() == Blocks.CHEST || blockItem.getBlock() == Blocks.BARREL) {
                return new Color(156, 102, 54, 220);
            }
            if (blockItem.getBlock() instanceof ShulkerBoxBlock shulkerBox) {
                int color = shulkerBox.getColor() == null
                        ? DyeColor.PURPLE.getTextureDiffuseColor()
                        : shulkerBox.getColor().getTextureDiffuseColor();
                return new Color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 220);
            }
            MapColor mapColor = blockItem.getBlock().defaultMapColor();
            if (mapColor != MapColor.NONE) {
                int color = mapColor.col;
                return new Color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 220);
            }
        }
        if (isBundle(stack)) {
            // 收纳袋颜色按物品对应的 DyeColor 取（与潜影盒取 DyeColor.getTextureDiffuseColor() 同思路）；
            // 彩色收纳袋颜色烤进贴图、不带 DYED_COLOR 组件，故不能用 DYED_COLOR 取色，否则会回退默认皮革色。
            // 基础（未染色）收纳袋在映射中无对应项，回落到默认皮革色。
            DyeColor dye = BUNDLE_COLORS.get(stack.getItem());
            int color = dye != null ? dye.getTextureDiffuseColor() : DyedItemColor.LEATHER_COLOR;
            return new Color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 220);
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
