package com.github.epsilon.modules.impl.render.bettertooltips;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.ItemStackTooltipEvent;
import com.github.epsilon.events.impl.TooltipDataEvent;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.settings.impl.KeybindSetting;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.utils.client.KeybindUtils;
import com.github.epsilon.utils.player.ContainerItemUtils;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.github.epsilon.Constants.mc;

public class BetterTooltips extends Module {
    public static final BetterTooltips INSTANCE = new BetterTooltips();
    public static final Color ECHEST_COLOR = new Color(0, 50, 50, 220);

    private final SettingGroup general = settingGroup("General");
    private final SettingGroup previews = settingGroup("Previews");
    private final SettingGroup other = settingGroup("Other");
    private final SettingGroup hideFlags = settingGroup("Hide Flags");

    private final EnumSetting<DisplayWhen> displayWhen = enumSetting("Display When", DisplayWhen.Keybind);
    private final KeybindSetting previewKey = keybindSetting("Preview Key", GLFW.GLFW_KEY_LEFT_ALT, () -> displayWhen.is(DisplayWhen.Keybind));
    private final BoolSetting openContents = boolSetting("Open Contents", true).group(general);
    private final KeybindSetting openContentsKey = keybindSetting("Open Contents Key", KeybindUtils.encodeMouseButton(GLFW.GLFW_MOUSE_BUTTON_MIDDLE), openContents::getValue).group(general);

    private final BoolSetting shulkers = boolSetting("Containers", true).group(previews);
    private final BoolSetting compactShulkerTooltip = boolSetting("Compact Shulker Tooltip", true).group(previews);
    private final BoolSetting echest = boolSetting("Ender Chests", true).group(previews);
    private final BoolSetting maps = boolSetting("Maps", true).group(previews);
    private final DoubleSetting mapsScale = doubleSetting("Map Scale", 1.0, 0.1, 1.0, 0.05, () -> maps.getValue()).group(previews);
    private final BoolSetting books = boolSetting("Books", true).group(previews);
    private final BoolSetting banners = boolSetting("Banners", true).group(previews);
    private final BoolSetting entitiesInBuckets = boolSetting("Entities In Buckets", true).group(previews);
    private final BoolSetting bundles = boolSetting("Bundles", true).group(previews);
    private final BoolSetting foodInfo = boolSetting("Food Info", true).group(previews);

    private final BoolSetting byteSize = boolSetting("Byte Size", true).group(other);
    private final EnumSetting<SortSize> sizeType = enumSetting("Byte Size Format", SortSize.Dynamic, byteSize::getValue).group(other);
    private final BoolSetting statusEffects = boolSetting("Status Effects", true).group(other);

    public final BoolSetting tooltip = boolSetting("Show Hidden Tooltip", false).group(hideFlags);
    public final BoolSetting additional = boolSetting("Show Hidden Components", false).group(hideFlags);

    private static final ItemStack[] PREVIEW = new ItemStack[27];
    private boolean openingPeek;

    private BetterTooltips() {
        super("Better Tooltips", Category.RENDER);
        displayWhen.group(general);
        previewKey.group(general);
    }

    @EventHandler
    private void appendTooltip(ItemStackTooltipEvent event) {
        if (!tooltip.getValue() && event.list().isEmpty()) {
            appendPreviewTooltipText(event, false);
            return;
        }

        if (statusEffects.getValue()) {
            if (event.itemStack().is(Items.SUSPICIOUS_STEW)) {
                SuspiciousStewEffects effects = event.itemStack().get(DataComponents.SUSPICIOUS_STEW_EFFECTS);
                if (effects != null) {
                    for (SuspiciousStewEffects.Entry entry : effects.effects()) {
                        event.appendStart(getStatusText(new MobEffectInstance(entry.effect(), entry.duration(), 0)));
                    }
                }
            } else {
                Consumable consumable = event.itemStack().get(DataComponents.CONSUMABLE);
                if (consumable != null) {
                    consumable.onConsumeEffects().stream()
                            .filter(ApplyStatusEffectsConsumeEffect.class::isInstance)
                            .map(ApplyStatusEffectsConsumeEffect.class::cast)
                            .flatMap(effect -> effect.effects().stream())
                            .forEach(effect -> event.appendStart(getStatusText(effect)));
                }
            }
        }

        if (foodInfo.getValue() && event.itemStack().has(DataComponents.FOOD)) {
            FoodProperties food = event.itemStack().get(DataComponents.FOOD);
            event.appendStart(Component.literal(String.format("Food %d (Saturation %.1f)", food.nutrition(), food.saturation()))
                    .withStyle(ChatFormatting.GRAY));
        }

        if (byteSize.getValue() && mc.player != null) {
            switch (ItemStack.CODEC.encodeStart(mc.player.registryAccess().createSerializationContext(NbtOps.INSTANCE), event.itemStack())) {
                case DataResult.Success<Tag> success -> {
                    try {
                        ByteCountDataOutput.INSTANCE.reset();
                        success.value().write(ByteCountDataOutput.INSTANCE);
                        int bytes = ByteCountDataOutput.INSTANCE.getCount();
                        String text = switch (sizeType.getValue()) {
                            case Bytes -> String.format("%d bytes", bytes);
                            case Kilobytes -> String.format("%.2f kB", bytes / 1024f);
                            case Megabytes -> String.format("%.4f MB", bytes / 1048576f);
                            case Dynamic -> bytes >= 1048576 ? String.format("%.2f MB", bytes / 1048576f)
                                    : bytes >= 1024 ? String.format("%.2f kB", bytes / 1024f)
                                    : String.format("%d bytes", bytes);
                        };
                        event.appendEnd(Component.literal(text).withStyle(ChatFormatting.DARK_GRAY));
                    } catch (Exception ignored) {
                        event.appendEnd(Component.literal("Error getting bytes.").withStyle(ChatFormatting.RED));
                    }
                }
                case DataResult.Error<Tag> ignored -> event.appendEnd(Component.literal("Error getting bytes.").withStyle(ChatFormatting.RED));
                default -> {
                }
            }
        }

        appendPreviewTooltipText(event, true);
    }

    @EventHandler
    private void getTooltipData(TooltipDataEvent event) {
        if (previewShulkers() && !event.itemStack.is(Items.ENDER_CHEST)
                && !(event.itemStack.getItem() instanceof BundleItem) && ContainerItemUtils.isContainer(event.itemStack)) {
            ContainerItemUtils.copyItems(event.itemStack, PREVIEW);
            if (hasPreviewItems()) {
                event.tooltipData = new ContainerTooltipComponent(PREVIEW, ContainerItemUtils.backgroundColor(event.itemStack));
            }
        } else if (event.itemStack.is(Items.ENDER_CHEST) && previewEChest()) {
            if (ContainerItemUtils.isContainer(event.itemStack)) {
                ContainerItemUtils.copyItems(event.itemStack, PREVIEW);
                if (hasPreviewItems()) event.tooltipData = new ContainerTooltipComponent(PREVIEW, ECHEST_COLOR);
            } else {
                event.tooltipData = new TextTooltipComponent(Component.literal("Unknown inventory.").withStyle(ChatFormatting.DARK_RED));
            }
        } else if (event.itemStack.is(Items.FILLED_MAP) && previewMaps()) {
            MapId mapId = event.itemStack.get(DataComponents.MAP_ID);
            if (mapId != null) event.tooltipData = new MapTooltipComponent(mapId.id(), mapsScale.getValue());
        } else if ((event.itemStack.is(Items.WRITABLE_BOOK) || event.itemStack.is(Items.WRITTEN_BOOK)) && previewBooks()) {
            Component page = getFirstPage(event.itemStack);
            if (page != null) {
                int pages = getBookPageCount(event.itemStack);
                event.tooltipData = new BookTooltipComponent(page.copy().append(Component.literal(String.format(" (%d pages)", pages)).withStyle(ChatFormatting.GRAY)));
            }
        } else if (event.itemStack.getItem() instanceof BannerItem && previewBanners()) {
            event.tooltipData = new BannerTooltipComponent(event.itemStack);
        } else if (event.itemStack.has(DataComponents.PROVIDES_BANNER_PATTERNS) && previewBanners()) {
            event.tooltipData = createBannerFromPatternItem(event.itemStack);
        } else if (event.itemStack.is(Items.SHIELD) && previewBanners()
                && !event.itemStack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers().isEmpty()) {
            event.tooltipData = createBannerFromShield(event.itemStack);
        } else if (event.itemStack.getItem() instanceof MobBucketItem && previewEntities()) {
            event.tooltipData = createEntityPreview(event.itemStack);
        } else if (event.itemStack.getItem() instanceof BundleItem && previewBundles()) {
            BundleContents contents = event.itemStack.get(DataComponents.BUNDLE_CONTENTS);
            if (contents != null && contents.size() > contents.getNumberOfItemsToShow()) {
                ItemStack[] items = contents.itemCopyStream().toArray(ItemStack[]::new);
                event.tooltipData = new BundleTooltipComponent(items, contents);
            }
        }
    }

    private TooltipComponent createEntityPreview(ItemStack stack) {
        if (mc.level == null || !(stack.getItem() instanceof MobBucketItem bucket)) return null;
        EntityType<?> type = ((com.github.epsilon.mixins.MixinMobBucketItem) (Object) bucket).epsilon$getType();
        LivingEntity entity = (LivingEntity) type.create(mc.level, EntitySpawnReason.NATURAL);
        if (entity == null) return null;
        CustomData data = stack.get(DataComponents.BUCKET_ENTITY_DATA);
        if (data == null) return null;
        entity.applyComponentsFromItemStack(stack);
        ((Bucketable) entity).loadFromBucketTag(data.copyTag());
        ((com.github.epsilon.mixins.MixinEntityWaterState) (Object) entity).epsilon$setInWater(true);
        return new EntityTooltipComponent(entity);
    }

    public void applyCompactShulkerTooltip(List<Optional<ItemStackTemplate>> stacks, Consumer<Component> consumer) {
        Object2IntMap<Item> counts = new Object2IntOpenHashMap<>();
        for (Optional<ItemStackTemplate> optional : stacks) {
            if (optional.isEmpty()) continue;
            ItemStackTemplate template = optional.get();
            if (template.count() > 0) counts.put(template.item().value(), counts.getInt(template.item().value()) + template.count());
        }
        counts.keySet().stream().sorted(Comparator.comparingInt(item -> -counts.getInt(item))).limit(5).forEach(item -> {
            MutableComponent text = item.components().get(DataComponents.ITEM_NAME).plainCopy();
            text.append(Component.literal(" x" + counts.getInt(item)).withStyle(ChatFormatting.GRAY));
            consumer.accept(text);
        });
        if (counts.size() > 5) consumer.accept(Component.translatable("item.container.more_items", counts.size() - 5).withStyle(ChatFormatting.ITALIC));
    }

    private void appendPreviewTooltipText(ItemStackTooltipEvent event, boolean spacer) {
        boolean show = !isPressed() && ((event.itemStack().is(Items.ENDER_CHEST) && echest.getValue())
                || (event.itemStack().is(Items.FILLED_MAP) && maps.getValue())
                || (event.itemStack().is(Items.WRITABLE_BOOK) && books.getValue())
                || (event.itemStack().is(Items.WRITTEN_BOOK) && books.getValue())
                || (event.itemStack().getItem() instanceof MobBucketItem && entitiesInBuckets.getValue())
                || (event.itemStack().getItem() instanceof BundleItem && bundles.getValue())
                || (event.itemStack().getItem() instanceof BannerItem && banners.getValue())
                || event.itemStack().has(DataComponents.PROVIDES_BANNER_PATTERNS) && banners.getValue()
                || (event.itemStack().is(Items.SHIELD) && banners.getValue()));
        if (show) {
            if (spacer) event.appendEnd(Component.literal(""));
            event.appendEnd(Component.literal("Hold " + ChatFormatting.YELLOW + KeybindUtils.format(previewKey.getValue()) + ChatFormatting.RESET + " to preview"));
        }
    }

    private MutableComponent getStatusText(MobEffectInstance effect) {
        MutableComponent text = Component.translatable(effect.getDescriptionId());
        if (effect.getAmplifier() != 0) text.append(String.format(" %d (%s)", effect.getAmplifier() + 1, MobEffectUtil.formatDuration(effect, 1, mc.level.tickRateManager().tickrate()).getString()));
        else text.append(String.format(" (%s)", MobEffectUtil.formatDuration(effect, 1, mc.level.tickRateManager().tickrate()).getString()));
        return text.withStyle(effect.getEffect().value().isBeneficial() ? ChatFormatting.BLUE : ChatFormatting.RED);
    }

    private Component getFirstPage(ItemStack stack) {
        if (stack.get(DataComponents.WRITABLE_BOOK_CONTENT) != null) {
            List<Filterable<String>> pages = stack.get(DataComponents.WRITABLE_BOOK_CONTENT).pages();
            return pages.isEmpty() ? null : Component.literal(pages.getFirst().get(false));
        }
        if (stack.get(DataComponents.WRITTEN_BOOK_CONTENT) != null) {
            List<Filterable<Component>> pages = stack.get(DataComponents.WRITTEN_BOOK_CONTENT).pages();
            return pages.isEmpty() ? null : pages.getFirst().get(false);
        }
        return null;
    }

    private int getBookPageCount(ItemStack stack) {
        if (stack.get(DataComponents.WRITABLE_BOOK_CONTENT) != null) return stack.get(DataComponents.WRITABLE_BOOK_CONTENT).pages().size();
        if (stack.get(DataComponents.WRITTEN_BOOK_CONTENT) != null) return stack.get(DataComponents.WRITTEN_BOOK_CONTENT).pages().size();
        return 0;
    }

    private BannerTooltipComponent createBannerFromPatternItem(ItemStack item) {
        HolderSet<BannerPattern> patterns = item.get(DataComponents.PROVIDES_BANNER_PATTERNS);
        if (patterns == null || patterns.size() == 0) return new BannerTooltipComponent(DyeColor.GRAY, BannerPatternLayers.EMPTY);
        return new BannerTooltipComponent(DyeColor.GRAY, new BannerPatternLayers.Builder().add(patterns.get(0), DyeColor.WHITE).build());
    }

    private BannerTooltipComponent createBannerFromShield(ItemStack item) {
        return new BannerTooltipComponent(item.getOrDefault(DataComponents.BASE_COLOR, DyeColor.WHITE), item.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY));
    }

    public boolean shouldOpenContents(int key, boolean mouse) {
        return openContents() && (mouse ? key == openContentsKey.getValue() : key == openContentsKey.getValue());
    }

    public boolean openContent(ItemStack stack) {
        return openContent(stack, null, -1, mc.screen);
    }

    public boolean openContent(ItemStack stack, AbstractContainerMenu sourceMenu, int sourceSlotId, Screen parentScreen) {
        if (!openContents() || stack.isEmpty()) return false;
        if (stack.getItem() instanceof BundleItem || ContainerItemUtils.isContainer(stack)) {
            if (!hasContainerContents(stack)) return false;
            boolean preserveMenu = sourceMenu != null && parentScreen instanceof AbstractContainerScreen<?>;
            openingPeek = preserveMenu;
            try {
                setScreenPreservingMouse(new ContainerInventoryScreen(stack, sourceMenu, sourceSlotId, parentScreen));
            } finally {
                openingPeek = false;
            }
            return true;
        }
        if (stack.is(Items.WRITABLE_BOOK) || stack.is(Items.WRITTEN_BOOK)) {
            mc.setScreen(new net.minecraft.client.gui.screens.inventory.BookViewScreen(net.minecraft.client.gui.screens.inventory.BookViewScreen.BookAccess.fromItem(stack)));
            return true;
        }
        return false;
    }

    public boolean openContents() {
        return isEnabled() && openContents.getValue();
    }

    public boolean isOpeningPeek() {
        return openingPeek;
    }

    private void setScreenPreservingMouse(Screen screen) {
        double mouseX = mc.mouseHandler.xpos();
        double mouseY = mc.mouseHandler.ypos();
        boolean mouseGrabbed = mc.mouseHandler.isMouseGrabbed();
        mc.setScreen(screen);
        if (mouseGrabbed) {
            mc.mouseHandler.setIgnoreFirstMove();
            GLFW.glfwSetCursorPos(mc.getWindow().handle(), mouseX, mouseY);
        }
    }

    private boolean hasPreviewItems() {
        for (ItemStack item : PREVIEW) {
            if (!item.isEmpty()) return true;
        }
        return false;
    }

    private boolean hasContainerContents(ItemStack stack) {
        if (stack.getItem() instanceof BundleItem) {
            BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
            return contents != null && !contents.isEmpty();
        }
        if (!ContainerItemUtils.isContainer(stack)) return false;
        ItemStack[] items = new ItemStack[ContainerItemUtils.getItemCount(stack)];
        ContainerItemUtils.copyItems(stack, items);
        for (ItemStack item : items) {
            if (!item.isEmpty()) return true;
        }
        return false;
    }

    public boolean previewShulkers() {
        return isEnabled() && shulkers.getValue();
    }

    public boolean shulkerCompactTooltip() {
        return isEnabled() && compactShulkerTooltip.getValue() && !previewShulkers();
    }

    private boolean previewEChest() { return isPressed() && echest.getValue(); }
    private boolean previewMaps() { return isPressed() && maps.getValue(); }
    private boolean previewBooks() { return isPressed() && books.getValue(); }
    private boolean previewBanners() { return isPressed() && banners.getValue(); }
    private boolean previewEntities() { return isPressed() && entitiesInBuckets.getValue(); }
    private boolean previewBundles() { return isPressed() && bundles.getValue(); }
    private boolean isPressed() { return displayWhen.is(DisplayWhen.Always) || KeybindUtils.isPressed(previewKey.getValue()); }

    public double mapsScale() { return mapsScale.getValue(); }

    public enum DisplayWhen { Keybind, Always }
    public enum SortSize { Bytes, Kilobytes, Megabytes, Dynamic }
}
