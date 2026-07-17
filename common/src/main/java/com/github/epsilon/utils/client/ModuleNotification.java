package com.github.epsilon.utils.client;

import com.github.epsilon.elements.impl.notification.NotificationMode;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.utils.player.ChatUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class ModuleNotification {

    private ModuleNotification() {
    }

    public static void send(NotificationChannel channel, String title, String message, NotificationMode mode, ChatFormatting color, int hash) {
        if (channel == NotificationChannel.HUD || channel == NotificationChannel.Both) {
            Managers.NOTIFICATION.notifyHud(title, message, mode, hash);
        }
        if ((channel == NotificationChannel.Chat || channel == NotificationChannel.Both)
                && ClientSetting.INSTANCE.chatNotify.getValue()) {
            ChatUtils.addChatMessage(Component.literal(title).append(" ").append(Component.literal(message).withStyle(color)), hash);
        }
    }
}
