package com.github.epsilon.elements.impl;

import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.elements.HudModule;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.level.GameType;

import java.awt.*;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public class PlayerRadarHUD extends HudModule {

    public static final PlayerRadarHUD INSTANCE = new PlayerRadarHUD();

    private final IntSetting limit = intSetting("Limit", 10, 1, 30, 1);
    private final BoolSetting showDistance = boolSetting("Show Distance", true);
    private final BoolSetting showHealth = boolSetting("Show Health", false);
    private final BoolSetting showPing = boolSetting("Show Ping", false);
    private final BoolSetting showGameMode = boolSetting("Show Game Mode", false);
    private final BoolSetting showFriends = boolSetting("Show Friends", true);
    private final DoubleSetting scale = doubleSetting("Scale", 0.72, 0.4, 2.0, 0.05);
    private final ColorSetting titleColor = colorSetting("Title Color", new Color(165, 175, 192, 235));
    private final ColorSetting playerColor = colorSetting("Player Color", new Color(245, 247, 250, 250));
    private final ColorSetting friendColor = colorSetting("Friend Color", new Color(80, 220, 150, 250));
    private final BoolSetting background = boolSetting("Background", true);
    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(15, 15, 15, 145), background::getValue);
    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);
    private final Map<String, TranslateComponent> displayTranslations = new HashMap<>();

    private PlayerRadarHUD() {
        super("Player Radar HUD", 0f, 0f, 120f, 40f);
    }

    @Override
    protected boolean shouldRenderTextShadow() {
        return !background.getValue();
    }

    @Override
    public void render(DeltaTracker deltaTracker) {
        if (nullCheck()) return;

        List<AbstractClientPlayer> players = mc.level.players().stream()
                .filter(player -> player != mc.player)
                .filter(player -> showFriends.getValue() || !Managers.FRIEND.isFriend(player))
                .sorted(Comparator.comparingDouble((AbstractClientPlayer player) -> player.distanceToSqr(mc.player))
                        .thenComparing(player -> player.getName().getString(), String.CASE_INSENSITIVE_ORDER))
                .limit(limit.getValue())
                .toList();
        TextRenderer renderer = textRendererSupplier.get();
        float textScale = scale.getValue().floatValue();
        float lineHeight = renderer.getHeight(textScale) + 2f;
        float width = renderer.getWidth(displayText("display.title", "Players"), textScale);

        for (AbstractClientPlayer player : players) width = Math.max(width, renderer.getWidth(line(player), textScale));
        float panelWidth = width + 10f;
        float panelHeight = (players.size() + 1) * lineHeight + 8f;
        setBounds(panelWidth, panelHeight);
        if (background.getValue()) {
            renderScope().roundRect(this.x, this.y, panelWidth, panelHeight, 4f, backgroundColor.getValue());
        }
        renderScope().text(displayText("display.title", "Players"), this.x + 5f, this.y + 4f, textScale, titleColor.getValue());

        float lineY = this.y + 4f + lineHeight;
        for (AbstractClientPlayer player : players) {
            boolean friend = Managers.FRIEND.isFriend(player);
            String name = player.getName().getString();
            float lineX = this.x + 5f;
            Color nameColor = friend ? friendColor.getValue() : playerColor.getValue();
            renderScope().text(name, lineX, lineY, textScale, nameColor);
            lineX += renderer.getWidth(name, textScale);
            if (showGameMode.getValue()) {
                GameModeDisplay gameMode = gameMode(player);
                String gameModeText = "[" + gameMode.text() + "]";
                renderScope().text(gameModeText, lineX, lineY, textScale,
                        gameMode.spectator() ? new Color(150, 150, 150, 255) : nameColor);
                lineX += renderer.getWidth(gameModeText, textScale);
            }
            if (showDistance.getValue()) {
                String distance = " " + Math.round(mc.player.distanceTo(player)) + distanceUnit();
                renderScope().text(distance, lineX, lineY, textScale, distanceColor(player.distanceTo(mc.player)));
                lineX += renderer.getWidth(distance, textScale);
            }
            if (showHealth.getValue()) {
                String health = " " + String.format(Locale.ROOT, "%.1f", player.getHealth()) + healthUnit();
                renderScope().text(health, lineX, lineY, textScale, healthColor(player));
                lineX += renderer.getWidth(health, textScale);
            }
            if (showPing.getValue()) {
                int latency = getPing(player);
                String ping = " " + latency + pingUnit();
                renderScope().text(ping, lineX, lineY, textScale, pingColor(latency));
            }
            lineY += lineHeight;
        }
    }

    private String line(AbstractClientPlayer player) {
        StringBuilder line = new StringBuilder(player.getName().getString());
        if (showGameMode.getValue()) line.append("[").append(gameMode(player).text()).append("]");
        if (showDistance.getValue()) line.append(" ").append(Math.round(mc.player.distanceTo(player))).append(distanceUnit());
        if (showHealth.getValue()) line.append(" ").append(String.format(Locale.ROOT, "%.1f", player.getHealth())).append(healthUnit());
        if (showPing.getValue()) line.append(" ").append(getPing(player)).append(pingUnit());
        return line.toString();
    }

    private String displayText(String key, String fallback) {
        if (translateComponent == null) return fallback;
        return displayTranslations.computeIfAbsent(key, translateComponent::createChild).getTranslatedName();
    }

    private String distanceUnit() {
        return displayText("display.distance unit", "m");
    }

    private String healthUnit() {
        return displayText("display.health unit", "hp");
    }

    private String pingUnit() {
        return displayText("display.ping unit", "ms");
    }

    private Color distanceColor(double distance) {
        float progress = Math.max(0f, Math.min(1f, (float) distance / 128f));
        Color near = new Color(235, 65, 55, 255);
        Color middle = new Color(245, 210, 60, 255);
        Color far = new Color(70, 210, 105, 255);
        return progress < 0.5f
                ? lerp(near, middle, progress * 2f)
                : lerp(middle, far, (progress - 0.5f) * 2f);
    }

    private Color pingColor(int ping) {
        float progress = Math.max(0f, Math.min(1f, ping / 300f));
        Color low = new Color(70, 210, 105, 255);
        Color middle = new Color(245, 210, 60, 255);
        Color high = new Color(235, 65, 55, 255);
        return progress < 0.5f
                ? lerp(low, middle, progress * 2f)
                : lerp(middle, high, (progress - 0.5f) * 2f);
    }

    private Color healthColor(AbstractClientPlayer player) {
        float maxHealth = player.getMaxHealth();
        float progress = maxHealth <= 0f ? 0f : Math.max(0f, Math.min(1f, player.getHealth() / maxHealth));
        return lerp(new Color(235, 65, 55, 255), new Color(245, 210, 60, 255), progress);
    }

    private Color lerp(Color from, Color to, float delta) {
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * delta),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * delta),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * delta),
                Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * delta)
        );
    }

    private int getPing(AbstractClientPlayer player) {
        if (mc.getConnection() == null) return 0;
        PlayerInfo info = mc.getConnection().getPlayerInfo(player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    private GameModeDisplay gameMode(AbstractClientPlayer player) {
        if (mc.getConnection() == null) return unknownGameMode();
        PlayerInfo info = mc.getConnection().getPlayerInfo(player.getUUID());
        if (info == null) return unknownGameMode();
        GameType gameType = info.getGameMode();
        return switch (gameType) {
            case CREATIVE -> new GameModeDisplay(displayText("display.game mode.creative", "C"), false);
            case SURVIVAL -> new GameModeDisplay(displayText("display.game mode.survival", "S"), false);
            case ADVENTURE -> new GameModeDisplay(displayText("display.game mode.adventure", "A"), false);
            case SPECTATOR -> new GameModeDisplay(displayText("display.game mode.spectator", "Sp"), true);
        };
    }

    private GameModeDisplay unknownGameMode() {
        return new GameModeDisplay(displayText("display.game mode.unknown", "Unknown"), false);
    }

    private record GameModeDisplay(String text, boolean spectator) {
    }
}
