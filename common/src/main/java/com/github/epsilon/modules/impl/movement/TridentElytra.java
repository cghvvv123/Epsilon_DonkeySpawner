package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.movement.elytrafly.ElytraFly;
import com.github.epsilon.modules.impl.player.AutoArmor;
import com.github.epsilon.modules.impl.player.ElytraSwap;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.utils.player.EnchantmentUtils;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

public final class TridentElytra extends Module {

    public static final TridentElytra INSTANCE = new TridentElytra();

    private final SettingGroup sgGeneral = settingGroup("General");
    private final BoolSetting autoEquipElytra = boolSetting("Auto Equip Elytra", true).group(sgGeneral);
    private final DoubleSetting handoffSpeed = doubleSetting("Handoff Speed", 2.0, 0.5, 5.0, 0.1).group(sgGeneral);
    private final BoolSetting handoffOnMove = boolSetting("Handoff On Move", true).group(sgGeneral);
    private final BoolSetting disableGravityDuringBoost = boolSetting("Disable Gravity", false).group(sgGeneral);

    private boolean active;
    private Double originalGravity;

    private TridentElytra() {
        super("Trident Elytra", Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        active = false;
        originalGravity = null;
    }

    @Override
    protected void onDisable() {
        releaseControl();
        restoreGravity();
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) return;

        boolean charging = isRiptideCharging();
        boolean spinning = mc.player.isAutoSpinAttack();
        if (charging || spinning) {
            if (!active) {
                active = true;
                pauseFlightControl();
            }
        }

        if (!active) return;

        // 激流结束后仍继续执行一次装备和重新起飞流程，确保离水/离地后能接回鞘翅飞行。
        handleBoost();
        if (charging || spinning) return;

        boolean moving = mc.options.keyUp.isDown()
                || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown()
                || mc.options.keyRight.isDown();
        if (ElytraFly.INSTANCE.isEnabled()
                && ((handoffOnMove.getValue() && moving)
                || mc.player.getDeltaMovement().length() < handoffSpeed.getValue()
                || mc.player.onGround())) {
            releaseControl();
        }

        if (disableGravityDuringBoost.getValue()) restoreGravity();
    }

    private boolean isRiptideCharging() {
        if (!mc.player.isUsingItem()) return false;
        ItemStack stack = mc.player.getUseItem();
        return mc.player.getMainHandItem().is(Items.TRIDENT)
                && stack.getItem() instanceof TridentItem
                && EnchantmentHelper.getTridentSpinAttackStrength(stack, mc.player) > 0.0F
                && (mc.level.isRaining() || mc.player.isInWaterOrRain());
    }

    private void handleBoost() {
        ElytraFly elytraFly = ElytraFly.INSTANCE;
        if (!canControlElytra(elytraFly)) return;
        if (mc.player.onGround() || mc.player.isInLiquid()) return;

        if (autoEquipElytra.getValue() && AutoArmor.INSTANCE.isElytraPlusActive()) {
            ElytraSwap.INSTANCE.ensureElytraEquipped();
        }

        if (!mc.player.isFallFlying()
                && isUsableElytra(mc.player.getItemBySlot(EquipmentSlot.CHEST))) {
            mc.player.startFallFlying();
            mc.getConnection().send(new ServerboundPlayerCommandPacket(
                    mc.player,
                    ServerboundPlayerCommandPacket.Action.START_FALL_FLYING
            ));
        }

        if (disableGravityDuringBoost.getValue()) {
            var gravity = mc.player.getAttribute(Attributes.GRAVITY);
            if (gravity != null) {
                if (originalGravity == null) originalGravity = gravity.getBaseValue();
                gravity.setBaseValue(mc.player.getDeltaMovement().y < 0.0D ? 0.0D : originalGravity);
            }
        }
    }

    private boolean canControlElytra(ElytraFly elytraFly) {
        return elytraFly.isEnabled();
    }

    private void pauseFlightControl() {
        if (canControlElytra(ElytraFly.INSTANCE)) ElytraFly.INSTANCE.setForcePause(true);
    }

    private void releaseControl() {
        ElytraFly.INSTANCE.setForcePause(false);
        if (mc.player != null && mc.player.onGround() && AutoArmor.INSTANCE.isElytraPlusActive()) {
            ElytraSwap.INSTANCE.swapToChestplateImmediately();
        }
        active = false;
    }

    private boolean isUsableElytra(ItemStack stack) {
        return LivingEntity.canGlideUsing(stack, EquipmentSlot.CHEST)
                && stack.getMaxDamage() - stack.getDamageValue() > 1
                && !EnchantmentUtils.hasEnchantment(stack, Enchantments.BINDING_CURSE);
    }

    private void restoreGravity() {
        if (mc.player == null) return;
        var gravity = mc.player.getAttribute(Attributes.GRAVITY);
        if (gravity != null) gravity.setBaseValue(originalGravity == null ? 0.08D : originalGravity);
    }
}
