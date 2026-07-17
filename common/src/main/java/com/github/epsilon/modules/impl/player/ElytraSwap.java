package com.github.epsilon.modules.impl.player;

import com.github.epsilon.elements.impl.notification.NotificationMode;
import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.movement.Flight;
import com.github.epsilon.modules.impl.movement.elytrafly.ElytraFly;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.settings.impl.KeybindSetting;
import com.github.epsilon.utils.client.KeybindUtils;
import com.github.epsilon.utils.client.ModuleNotification;
import com.github.epsilon.utils.client.NotificationChannel;
import com.github.epsilon.utils.player.ClickSlotUtils;
import com.github.epsilon.utils.player.EnchantmentUtils;
import com.github.epsilon.utils.player.InvUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import org.lwjgl.glfw.GLFW;

import java.util.function.Predicate;

public class ElytraSwap extends Module {

    public static final ElytraSwap INSTANCE = new ElytraSwap();

    private enum SwapMode {
        Manual,
        Auto
    }

    private enum ElytraPriority {
        HighQualityFirst,
        LowQualityFirst
    }

    private final SettingGroup sgGeneral = settingGroup("General");
    private final SettingGroup sgAuto = settingGroup("Auto");

    private final EnumSetting<SwapMode> mode = enumSetting("Mode", SwapMode.Manual).group(sgGeneral);
    private final KeybindSetting activateKey = keybindSetting("Activate Key", GLFW.GLFW_KEY_G, () -> mode.is(SwapMode.Manual)).group(sgGeneral);
    private final IntSetting swapDelay = intSetting("Delay", 0, 0, 20, 1, () -> mode.is(SwapMode.Manual)).group(sgGeneral);
    private final BoolSetting switchBack = boolSetting("Switch Back", true, () -> mode.is(SwapMode.Manual)).group(sgGeneral);
    private final IntSetting switchDelay = intSetting("Switch Delay", 0, 0, 20, 1, () -> mode.is(SwapMode.Manual)).group(sgGeneral);
    private final BoolSetting moveToSlot = boolSetting("Move to Slot", true, () -> mode.is(SwapMode.Manual)).group(sgGeneral);
    private final IntSetting elytraSlotSetting = intSetting("Elytra Slot", 9, 1, 9, 1, () -> mode.is(SwapMode.Manual)).group(sgGeneral);

    private final BoolSetting autoReplace = boolSetting("Auto Replace", true, () -> mode.is(SwapMode.Auto)).group(sgAuto);
    private final IntSetting replaceThreshold = intSetting("Replace Threshold", 2, 1, 50, 1, () -> mode.is(SwapMode.Auto) && autoReplace.getValue()).group(sgAuto);
    private final BoolSetting emergencyHover = boolSetting("Emergency Hover", true, () -> mode.is(SwapMode.Auto)).group(sgAuto);
    private final EnumSetting<ElytraPriority> elytraPriority = enumSetting("Elytra Priority", ElytraPriority.LowQualityFirst, () -> mode.is(SwapMode.Auto)).group(sgAuto);
    private final BoolSetting swapBackImmediately = boolSetting("Swap Back Immediately", true, () -> mode.is(SwapMode.Auto)).group(sgAuto);
    private final BoolSetting swapBackImmediatelyForMace = boolSetting("Only When Holding a Mace", true,
            () -> mode.is(SwapMode.Auto) && swapBackImmediately.getValue()).group(sgAuto);
    private final BoolSetting lowDurabilityWarning = boolSetting("Low Durability Warning", true, () -> mode.is(SwapMode.Auto)).group(sgAuto);
    private final IntSetting warningThreshold = intSetting("Warning Threshold", 20, 1, 100, 1, () -> mode.is(SwapMode.Auto) && lowDurabilityWarning.getValue()).group(sgAuto);
    private final BoolSetting onlyWhenLastElytra = boolSetting("Only When Last Elytra", true, () -> mode.is(SwapMode.Auto) && lowDurabilityWarning.getValue()).group(sgAuto);
    private final EnumSetting<NotificationChannel> notificationChannel = enumSetting("Notification Channel", NotificationChannel.Both, () -> mode.is(SwapMode.Auto)).group(sgAuto);

    private boolean isSwinging;
    private boolean isItemSwapped;
    private int swapCounter;
    private int switchCounter;
    private int originalSlot;
    private boolean isKeyDown;
    private boolean wasFlightActive;
    /** 本次鞘翅飞行会话已经开始，关闭平飞后直到落地前保持鞘翅。 */
    private boolean flightSessionActive;
    private boolean pendingChestplateSwap;
    private boolean emergencyActive;
    private boolean emergencyExitRequested;
    private boolean jumpPressedLastTick;
    private int lastNotificationHash;
    private int lastWarnedDurability = -1;
    private Double emergencyGravity;

    private ElytraSwap() {
        super("Elytra Swap", Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        resetState();
        isKeyDown = false;
        wasFlightActive = ElytraFly.INSTANCE.isEnabled();
        flightSessionActive = wasFlightActive || (mc.player != null && mc.player.isFallFlying());
        pendingChestplateSwap = false;
        emergencyActive = false;
        emergencyExitRequested = false;
        jumpPressedLastTick = false;
        lastNotificationHash = 0;
        lastWarnedDurability = -1;
        emergencyGravity = null;
    }

    @Override
    protected void onDisable() {
        restoreEmergency();
        resetState();
        flightSessionActive = false;
        pendingChestplateSwap = false;
        emergencyExitRequested = false;
        jumpPressedLastTick = false;
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) return;

        if (mode.is(SwapMode.Auto)) {
            onAutoTick();
        } else {
            onManualTick();
        }
    }

    private void onManualTick() {
        boolean pressed = KeybindUtils.isPressed(activateKey.getValue());
        if (!pressed) isKeyDown = false;
        if (!pressed) return;
        if (isKeyDown && originalSlot == -1) return;
        isKeyDown = true;

        if (originalSlot == -1) originalSlot = mc.player.getInventory().getSelectedSlot();
        if (swapCounter < swapDelay.getValue()) {
            swapCounter++;
            return;
        }

        boolean wearingElytra = mc.player.getItemBySlot(EquipmentSlot.CHEST).has(DataComponents.GLIDER);
        Predicate<ItemStack> predicate = wearingElytra
                ? stack -> {
                    if (stack.isEmpty()) return false;
                    var equippable = stack.get(DataComponents.EQUIPPABLE);
                    return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
                }
                : stack -> stack.has(DataComponents.GLIDER);

        if (!isItemSwapped) {
            int targetSlot = InvUtils.findInHotbar(predicate).slot();
            if (targetSlot == -1) {
                if (!moveToSlot.getValue()) {
                    resetState();
                    return;
                }
                int invSlot = InvUtils.find(predicate).slot();
                if (invSlot == -1) {
                    resetState();
                    return;
                }
                int containerSlot = invSlot < 9 ? invSlot + 36 : invSlot;
                ClickSlotUtils.swap(mc.player.containerMenu.containerId, containerSlot, elytraSlotSetting.getValue() - 1);
                swapCounter = 0;
                return;
            }
            InvUtils.swap(targetSlot, false);
            isItemSwapped = true;
        }

        if (!isSwinging) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            mc.player.swing(InteractionHand.MAIN_HAND);
            isSwinging = true;
        }

        if (switchBack.getValue()) {
            if (switchCounter < switchDelay.getValue()) {
                switchCounter++;
            } else {
                if (originalSlot != -1) InvUtils.swap(originalSlot, false);
                resetState();
            }
        } else {
            resetState();
        }
    }

    private void onAutoTick() {
        boolean flightActive = ElytraFly.INSTANCE.isEnabled();
        if (flightActive || mc.player.isFallFlying()) flightSessionActive = true;

        if (wasFlightActive && !flightActive) {
            if (shouldImmediatelySwapBack()) {
                pendingChestplateSwap = false;
                swapToChestplateImmediately();
            } else {
                pendingChestplateSwap = true;
            }
        }
        if (pendingChestplateSwap && (mc.player.onGround() || mc.player.isPassenger()) && !mc.player.isFallFlying()) {
            pendingChestplateSwap = false;
            flightSessionActive = false;
            swapToChestplateImmediately();
        }

        // 按源代码流程，Shift 必须立即强制退出紧急卡空。
        if (emergencyActive && mc.options.keyShift.isDown()) {
            emergencyExitRequested = true;
            restoreEmergency();
            notifyState(EpsilonTranslations.ElytraSwap.EMERGENCY_DISABLED.getTranslatedName(), NotificationMode.Info, ChatFormatting.GREEN);
        }

        // Auto 模式只与 AutoArmor 的 ElytraPlus 联动。
        if (!AutoArmor.INSTANCE.isElytraPlusActive()) {
            restoreEmergency();
            wasFlightActive = flightActive;
            if ((mc.player.onGround() || mc.player.isPassenger()) && !mc.player.isFallFlying()) {
                flightSessionActive = false;
            }
            return;
        }

        if (emergencyActive) {
            maintainEmergency();
            if (emergencyActive) {
                wasFlightActive = flightActive;
                return;
            }
        }

        boolean airborne = !mc.player.onGround() && !mc.player.isPassenger();
        if (!airborne) emergencyExitRequested = false;
        boolean equipped = isUsableElytra(mc.player.getItemBySlot(EquipmentSlot.CHEST));

        // 源代码在玩家进入空中后就确保胸甲栏有可用鞘翅，EasyTakeoff 随后才能发送起飞包。
        if (flightActive && airborne && !equipped) equipped = ensureElytraEquipped();
        if (flightActive && !airborne && mc.options.keyJump.isDown() && !jumpPressedLastTick) {
            equipped = ensureElytraEquipped();
        }
        jumpPressedLastTick = mc.options.keyJump.isDown();

        if (flightActive && mc.player.isFallFlying()) {
            evaluateElytraState();
            warnLowDurability();
        }
        if ((mc.player.onGround() || mc.player.isPassenger()) && !mc.player.isFallFlying()) {
            flightSessionActive = false;
        }
        wasFlightActive = flightActive;
    }

    /** 按源 ElytraSwap 的顺序区分装备、替换和紧急卡空。 */
    private void evaluateElytraState() {
        ItemStack current = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        boolean wearingValidElytra = current.has(DataComponents.GLIDER)
                && current.getMaxDamage() - current.getDamageValue() > 1;

        if (!wearingValidElytra) {
            if (findBestElytraSlot(1) != -1) {
                if (!ensureElytraEquipped() && emergencyHover.getValue()) activateEmergency();
            } else if (emergencyHover.getValue()) {
                activateEmergency();
            }
            return;
        }

        if (!autoReplace.getValue()) return;
        int durability = current.getMaxDamage() - current.getDamageValue();
        if (durability > replaceThreshold.getValue()) return;

        int replacement = findBestElytraSlot(1);
        if (replacement == -1) {
            // 仍可滑翔的唯一鞘翅不属于紧急情况，即使它已经低于替换阈值。
            return;
        }

        ItemStack candidate = mc.player.getInventory().getItem(replacement);
        int candidateDurability = candidate.getMaxDamage() - candidate.getDamageValue();
        if (candidateDurability > replaceThreshold.getValue()) replaceElytra();
    }

    /** 等待落地期间阻止 AutoArmor 抢先换回胸甲。 */
    public boolean isWaitingForFlightLanding() {
        return mode.is(SwapMode.Auto)
                && (pendingChestplateSwap || (flightSessionActive && !ElytraFly.INSTANCE.isEnabled()))
                && mc.player != null
                && !mc.player.onGround()
                && !mc.player.isPassenger();
    }

    private boolean shouldImmediatelySwapBack() {
        return swapBackImmediately.getValue()
                && (!swapBackImmediatelyForMace.getValue() || isHoldingMace());
    }

    private boolean isHoldingMace() {
        return mc.player.getMainHandItem().is(Items.MACE)
                || mc.player.getOffhandItem().is(Items.MACE);
    }

    public boolean ensureElytraEquipped() {
        if (nullCheck()) return false;
        if (isUsableElytra(mc.player.getItemBySlot(EquipmentSlot.CHEST))) return true;
        int slot = findUsableElytraSlot();
        if (slot == -1) return false;
        if (!swapArmor(slot)) return false;
        return isUsableElytra(mc.player.getItemBySlot(EquipmentSlot.CHEST));
    }

    public boolean isAutoMode() {
        return isEnabled() && mode.is(SwapMode.Auto) && AutoArmor.INSTANCE.isElytraPlusActive();
    }

    public boolean swapToChestplateImmediately() {
        if (nullCheck()) return false;
        ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (!chest.has(DataComponents.GLIDER)) return isChestplate(chest);
        int chestplate = findChestplateSlot();
        if (chestplate == -1) return false;
        if (mc.player.isFallFlying()) mc.player.stopFallFlying();
        return swapArmor(chestplate) && isChestplate(mc.player.getItemBySlot(EquipmentSlot.CHEST));
    }

    private void replaceElytra() {
        int slot = findBestElytraSlot(replaceThreshold.getValue());
        if (slot == -1) {
            notifyState(EpsilonTranslations.ElytraSwap.NO_USABLE_REPLACEMENT.getTranslatedName(), NotificationMode.Error, ChatFormatting.RED);
            return;
        }
        boolean flying = mc.player.isFallFlying();
        if (!swapArmor(slot)) return;
        if (flying && !mc.player.isFallFlying()) restartFallFlying();
        notifyState(EpsilonTranslations.ElytraSwap.REPLACED_LOW_DURABILITY.getTranslatedName(), NotificationMode.Info, ChatFormatting.YELLOW);
    }

    private int findUsableElytraSlot() {
        return findBestElytraSlot(1);
    }

    private int findBestElytraSlot(int minimumDurability) {
        int bestSlot = -1;
        int bestScore = elytraPriority.is(ElytraPriority.HighQualityFirst)
                ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int size = mc.player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            if (i == 38 || i == 40) continue;
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!LivingEntity.canGlideUsing(stack, EquipmentSlot.CHEST)) continue;
            if (EnchantmentUtils.hasEnchantment(stack, Enchantments.BINDING_CURSE)) continue;
            int durability = stack.getMaxDamage() - stack.getDamageValue();
            if (durability <= minimumDurability) continue;
            int score = durability
                    + (EnchantmentUtils.hasEnchantment(stack, Enchantments.MENDING) ? 500 : 0)
                    + (EnchantmentUtils.hasEnchantment(stack, Enchantments.UNBREAKING) ? 200 : 0);
            boolean better = elytraPriority.is(ElytraPriority.HighQualityFirst)
                    ? score > bestScore
                    : score < bestScore;
            if (better) {
                bestSlot = i;
                bestScore = score;
            }
        }
        return bestSlot;
    }

    private int findChestplateSlot() {
        int size = mc.player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            if (i == 38 || i == 40) continue;
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isChestplate(stack) && !EnchantmentUtils.hasEnchantment(stack, Enchantments.BINDING_CURSE)) return i;
        }
        return -1;
    }

    private boolean isChestplate(ItemStack stack) {
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.CHEST && !stack.has(DataComponents.GLIDER);
    }

    private boolean isLowDurability(ItemStack stack) {
        return isUsableElytra(stack)
                && stack.getMaxDamage() - stack.getDamageValue() <= replaceThreshold.getValue();
    }

    private boolean isUsableElytra(ItemStack stack) {
        return LivingEntity.canGlideUsing(stack, EquipmentSlot.CHEST)
                && stack.getMaxDamage() - stack.getDamageValue() > 1
                && !EnchantmentUtils.hasEnchantment(stack, Enchantments.BINDING_CURSE);
    }

    private void warnLowDurability() {
        if (!lowDurabilityWarning.getValue()) {
            lastWarnedDurability = -1;
            return;
        }
        ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (!chest.has(DataComponents.GLIDER)) {
            lastWarnedDurability = -1;
            return;
        }
        int remaining = chest.getMaxDamage() - chest.getDamageValue();
        if (remaining > warningThreshold.getValue()
                || (onlyWhenLastElytra.getValue() && findUsableElytraSlot() != -1)) {
            lastWarnedDurability = -1;
            return;
        }
        if (remaining != lastWarnedDurability) {
            lastWarnedDurability = remaining;
            notifyState(EpsilonTranslations.ElytraSwap.DURABILITY_LOW.getTranslatedName().replace("{durability}", String.valueOf(remaining)), NotificationMode.Error, ChatFormatting.RED);
        }
    }

    private boolean swapArmor(int slot) {
        if (slot < 0 || slot >= mc.player.getInventory().getContainerSize()) return false;
        ItemStack candidate = mc.player.getInventory().getItem(slot);
        if (candidate.isEmpty()) return false;

        boolean wearingElytra = LivingEntity.canGlideUsing(
                mc.player.getItemBySlot(EquipmentSlot.CHEST), EquipmentSlot.CHEST);
        int containerSlot = slot < 9 ? slot + 36 : slot;
        ClickSlotUtils.click(mc.player.containerMenu.containerId, containerSlot);
        ClickSlotUtils.click(mc.player.containerMenu.containerId, 6);
        ClickSlotUtils.click(mc.player.containerMenu.containerId, containerSlot);

        ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        return wearingElytra
                ? !LivingEntity.canGlideUsing(chest, EquipmentSlot.CHEST)
                : LivingEntity.canGlideUsing(chest, EquipmentSlot.CHEST);
    }

    private void restartFallFlying() {
        mc.getConnection().send(new ServerboundPlayerCommandPacket(
                mc.player,
                ServerboundPlayerCommandPacket.Action.START_FALL_FLYING
        ));
        mc.player.startFallFlying();
    }

    private void activateEmergency() {
        if (emergencyActive
                || emergencyExitRequested
                || mc.player.onGround()
                || mc.player.isPassenger()
                || mc.player.isInLiquid()
                || mc.player.hasEffect(MobEffects.LEVITATION)
                || mc.player.hasEffect(MobEffects.SLOW_FALLING)
                || Flight.INSTANCE.isEnabled()) return;
        emergencyActive = true;
        ElytraFly.INSTANCE.resetSmartInfForEmergency();
        var gravity = mc.player.getAttribute(Attributes.GRAVITY);
        if (gravity != null) {
            emergencyGravity = gravity.getBaseValue() == 0.0D ? 0.08D : gravity.getBaseValue();
            gravity.setBaseValue(0.0D);
        }
        notifyState(EpsilonTranslations.ElytraSwap.EMERGENCY_ENABLED.getTranslatedName(), NotificationMode.Error, ChatFormatting.RED);
    }

    private void maintainEmergency() {
        var gravity = mc.player.getAttribute(Attributes.GRAVITY);
        if (gravity != null) gravity.setBaseValue(0.0D);
        ElytraFly.INSTANCE.resetSmartInfForEmergency();

        boolean hasReplacement = findUsableElytraSlot() != -1;
        boolean currentIsUsable = isUsableElytra(mc.player.getItemBySlot(EquipmentSlot.CHEST));
        if (mc.options.keyShift.isDown()
                || mc.player.onGround()
                || mc.player.isPassenger()
                || hasReplacement
                || currentIsUsable) {
            if (mc.options.keyShift.isDown()) emergencyExitRequested = true;
            else if (hasReplacement || currentIsUsable || mc.player.onGround() || mc.player.isPassenger()) emergencyExitRequested = false;
            restoreEmergency();
            notifyState(EpsilonTranslations.ElytraSwap.EMERGENCY_DISABLED.getTranslatedName(), NotificationMode.Info, ChatFormatting.GREEN);
        }
    }

    public boolean isEmergencyActive() {
        return emergencyActive;
    }

    private void restoreEmergency() {
        if (!emergencyActive) return;
        emergencyActive = false;
        var gravity = mc.player.getAttribute(Attributes.GRAVITY);
        if (gravity != null) gravity.setBaseValue(emergencyGravity == null ? 0.08D : emergencyGravity);
        emergencyGravity = null;
    }

    private void notifyState(String message, NotificationMode mode, ChatFormatting color) {
        int hash = (getName() + ":" + message).hashCode();
        if (hash == lastNotificationHash) return;
        lastNotificationHash = hash;
        ModuleNotification.send(notificationChannel.getValue(), getTranslatedName(), message, mode, color, hash);
    }

    private void resetState() {
        originalSlot = -1;
        switchCounter = 0;
        swapCounter = 0;
        isSwinging = false;
        isItemSwapped = false;
    }
}
