package com.github.epsilon.modules.impl.player;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.elements.impl.notification.NotificationMode;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.MousePressEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.client.ModuleNotification;
import com.github.epsilon.utils.client.NotificationChannel;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

public class MiddleClickExtra extends Module {

    public static final MiddleClickExtra INSTANCE = new MiddleClickExtra();

    private final EnumSetting<NotificationChannel> notificationChannel =
            enumSetting("Notification Channel", NotificationChannel.Both);

    private MiddleClickExtra() {
        super("Middle Click Extra", Category.PLAYER);
    }

    @EventHandler
    private void onMousePress(MousePressEvent event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                || event.getAction() != InputConstants.PRESS
                || nullCheck()
                || mc.gameMode == null
                || mc.screen != null
                || mc.getOverlay() != null) return;

        if (mc.gameMode.isSpectator()) return;

        // 创造模式对着方块时保留原版中键取方块行为。
        if (mc.player.isCreative()
                && mc.hitResult != null
                && mc.hitResult.getType() == HitResult.Type.BLOCK) return;

        event.cancel();

        if (toggleTargetFriend()) return;

        // 0-35 为快捷栏+背包，40 为副手。
        FindItemResult pearl = InvUtils.find(stack -> stack.is(Items.ENDER_PEARL), 0, 40);
        if (!pearl.found()) {
            notifyMessage(EpsilonTranslations.MiddleClickExtra.NO_ENDER_PEARL,
                    NotificationMode.Error, ChatFormatting.RED);
            return;
        }

        int slot = pearl.slot();

        // 副手里的末影珍珠直接用手持副手使用。
        if (slot == 40) {
            InteractionResult result = mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
            if (result.consumesAction()) mc.player.swing(InteractionHand.OFF_HAND);
            return;
        }

        int originalSlot = mc.player.getInventory().getSelectedSlot();
        boolean hotbar = slot >= 0 && slot <= 8;

        if (hotbar) {
            // 快捷栏珍珠：直接切换选中槽位即可（本地即时生效）。
            InvUtils.swap(slot, false);
        } else {
            // 背包珍珠：通过背包菜单交换到当前选中槽位。
            // handleContainerInput 会在发送数据包前先在客户端本地应用该交换，因此可立即使用。
            InvUtils.invSwap(slot);
        }

        try {
            InteractionResult result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            if (result.consumesAction()) mc.player.swing(InteractionHand.MAIN_HAND);
        } finally {
            if (hotbar) {
                InvUtils.swap(originalSlot, false);
            } else {
                InvUtils.invSwapBack();
            }
        }
    }

    private boolean toggleTargetFriend() {
        if (!(mc.hitResult instanceof EntityHitResult entityHit)
                || !(entityHit.getEntity() instanceof Player target)
                || target == mc.player
                || !mc.player.isWithinEntityInteractionRange(target, 0.0)) return false;

        String name = target.getGameProfile().name();
        if (Managers.FRIEND.isFriend(name)) {
            Managers.FRIEND.removeFriend(name);
            notifyMessage(EpsilonTranslations.MiddleClickExtra.FRIEND_REMOVED,
                    name, NotificationMode.Info, ChatFormatting.YELLOW);
        } else {
            Managers.FRIEND.addFriend(name);
            notifyMessage(EpsilonTranslations.MiddleClickExtra.FRIEND_ADDED,
                    name, NotificationMode.Success, ChatFormatting.GREEN);
        }
        return true;
    }

    private void notifyMessage(TranslateComponent template, NotificationMode mode, ChatFormatting color) {
        notifyMessage(template, null, mode, color);
    }

    private void notifyMessage(TranslateComponent template, String name, NotificationMode mode, ChatFormatting color) {
        String message = template.getTranslatedName();
        if (name != null) message = message.replace("{name}", name);
        ModuleNotification.send(notificationChannel.getValue(), getTranslatedName(), message,
                mode, color, Objects.hash(getName(), message));
    }
}
