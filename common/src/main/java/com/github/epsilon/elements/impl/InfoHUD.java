package com.github.epsilon.elements.impl;

import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.elements.HudModule;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public abstract class InfoHUD extends HudModule {

    protected enum SpeedMode {
        Horizontal,
        AllDirections
    }

    protected enum HorizontalAlignment {
        Left,
        Center,
        Right
    }

    protected final DoubleSetting scale = doubleSetting("Scale", 0.72, 0.4, 2.0, 0.02);
    protected final DoubleSetting padding = doubleSetting("Padding", 5.0, 0.0, 16.0, 0.5);
    protected final EnumSetting<HorizontalAlignment> alignment = enumSetting("Alignment", HorizontalAlignment.Left);
    protected final DoubleSetting cornerRadius = doubleSetting("Corner Radius", 4.0, 0.0, 14.0, 0.5);
    protected final BoolSetting background = boolSetting("Background", true);
    protected final ColorSetting backgroundColor = colorSetting("Background Color", new Color(15, 15, 15, 150), background::getValue);
    protected final BoolSetting backgroundBlur = boolSetting("Background Blur", true, background::getValue);
    protected final ColorSetting labelColor = colorSetting("Label Color", new Color(130, 180, 255, 255));
    protected final ColorSetting valueColor = colorSetting("Value Color", new Color(245, 247, 250, 250));
    protected final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);
    protected final float minimumWidth;
    private final Map<String, TranslateComponent> displayTranslations = new HashMap<>();

    protected InfoHUD(String name, float width, float height) {
        super(name, 0f, 0f, width, height);
        minimumWidth = width;
    }

    @Override
    protected boolean shouldRenderTextShadow() {
        return !background.getValue();
    }

    @Override
    protected HorizontalAnchor getResizeHorizontalAnchor() {
        return switch (alignment.getValue()) {
            case Left -> HorizontalAnchor.Left;
            case Center -> HorizontalAnchor.Center;
            case Right -> HorizontalAnchor.Right;
        };
    }

    protected abstract String label();

    protected abstract String value();

    protected final String displayText(String key, String fallback) {
        if (translateComponent == null) return fallback;
        return displayTranslations.computeIfAbsent(key, translateComponent::createChild).getTranslatedName();
    }

    @Override
    public void render(DeltaTracker deltaTracker) {
        if (nullCheck()) return;

        String value = value();
        if (value == null || value.isBlank()) {
            setBounds(20f, 20f);
            return;
        }

        TextRenderer renderer = textRendererSupplier.get();
        float textScale = scale.getValue().floatValue();
        float pad = padding.getValue().floatValue();
        float labelGap = renderer.getWidth(" ", textScale);
        float labelWidth = renderer.getWidth(label(), textScale);
        float colonWidth = renderer.getWidth(":", textScale);
        float valueWidth = renderer.getWidth(value, textScale);
        float panelWidth = Math.max(minimumWidth, pad * 2f + labelWidth + colonWidth + labelGap + valueWidth);
        float panelHeight = pad * 2f + renderer.getHeight(textScale);
        setBounds(panelWidth, panelHeight);

        drawBackground(panelWidth, panelHeight);
        float lineY = this.y + pad;
        float contentWidth = labelWidth + colonWidth + labelGap + valueWidth;
        float labelX = alignedX(panelWidth, contentWidth, pad);
        float colonX = labelX + labelWidth;
        float valueX = colonX + colonWidth + labelGap;
        renderScope().text(label(), labelX, lineY, textScale, labelColor.getValue());
        renderScope().text(":", colonX, lineY, textScale, labelColor.getValue());
        renderScope().text(value, valueX, lineY, textScale, valueColor.getValue());
    }

    protected final float alignedX(float panelWidth, float contentWidth, float pad) {
        float available = panelWidth - pad * 2f - contentWidth;
        return this.x + pad + switch (alignment.getValue()) {
            case Left -> 0f;
            case Center -> available / 2f;
            case Right -> available;
        };
    }

    protected final void drawBackground(float width, float height) {
        if (!background.getValue()) return;
        float radius = cornerRadius.getValue().floatValue();
        if (backgroundBlur.getValue()) BlurShader.INSTANCE.render(this.x, this.y, width, height, radius, 5);
        renderScope().roundRect(this.x, this.y, width, height, radius, backgroundColor.getValue());
    }

    protected final String format(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    protected final int ping() {
        if (mc.getConnection() == null || mc.player == null) return 0;
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    protected final double speed(SpeedMode mode) {
        Vec3 movement = mc.player.getKnownSpeed();
        return (mode == SpeedMode.Horizontal ? movement.horizontalDistance() : movement.length()) * 20.0;
    }

    protected final String gameMode() {
        if (mc.gameMode == null) return displayText("display.unknown", "Unknown");
        GameType mode = mc.gameMode.getPlayerMode();
        if (mode == null) return displayText("display.unknown", "Unknown");
        return switch (mode) {
            case SURVIVAL -> displayText("display.survival", "Survival");
            case CREATIVE -> displayText("display.creative", "Creative");
            case ADVENTURE -> displayText("display.adventure", "Adventure");
            case SPECTATOR -> displayText("display.spectator", "Spectator");
        };
    }

    protected final String breakingProgress() {
        if (mc.gameMode == null || !mc.gameMode.isDestroying()) return "0%";
        return Mth.clamp(Math.round(mc.gameMode.destroyProgress * 100.0f), 0, 100) + "%";
    }

    protected final String server() {
        ServerData currentServer = mc.getCurrentServer();
        return currentServer == null ? displayText("display.singleplayer", "Singleplayer") : currentServer.ip;
    }

    protected final String weather() {
        if (mc.level.isThundering()) return displayText("display.thunder", "Thunder");
        if (mc.level.isRaining()) return displayText("display.rain", "Rain");
        return displayText("display.clear", "Clear");
    }

    protected final String biome() {
        return mc.level.getBiome(mc.player.blockPosition()).unwrapKey()
                .map(key -> Component.translatable(key.identifier().toLanguageKey("biome")).getString())
                .orElse(displayText("display.unknown", "Unknown"));
    }

    protected final String worldTime() {
        long ticks = Math.floorMod(mc.level.getOverworldClockTime(), 24000L);
        long totalMinutes = (ticks * 60L / 1000L + 360L) % 1440L;
        return String.format(Locale.ROOT, "%02d:%02d", totalMinutes / 60L, totalMinutes % 60L);
    }

    protected final String rotation() {
        float yaw = Mth.wrapDegrees(mc.player.getYRot());
        int directionIndex = Mth.floor(yaw / 45f + 0.5f) & 7;
        String direction = switch (directionIndex) {
            case 0 -> "south";
            case 1 -> "southwest";
            case 2 -> "west";
            case 3 -> "northwest";
            case 4 -> "north";
            case 5 -> "northeast";
            case 6 -> "east";
            default -> "southeast";
        };
        String directionName = displayText("display.direction." + direction, direction);
        String horizontalLabel = displayText("display.horizontal", "Horizontal");
        String pitchLabel = displayText("display.pitch", "Pitch");
        return directionName + " (" + horizontalLabel + ": " + format(yaw, 1)
                + ", " + pitchLabel + ": " + format(mc.player.getXRot(), 1) + ")";
    }

    public static final class FpsHUD extends InfoHUD {
        public static final FpsHUD INSTANCE = new FpsHUD();

        private FpsHUD() {
            super("FPS HUD", 80f, 20f);
        }

        @Override
        protected String label() {
            return displayText("display.label", "FPS");
        }

        @Override
        protected String value() {
            return Integer.toString(mc.getFps());
        }
    }

    public static final class TpsHUD extends InfoHUD {
        public static final TpsHUD INSTANCE = new TpsHUD();
        private long lastTimePacketAt;
        private long lastServerGameTime;
        private double serverTps = 20.0;

        private TpsHUD() {
            super("TPS HUD", 80f, 20f);
        }

        @Override
        protected void onEnable() {
            lastTimePacketAt = 0L;
            lastServerGameTime = 0L;
            serverTps = 20.0;
        }

        @EventHandler
        private void onPacketReceive(PacketEvent.Receive event) {
            if (!(event.getPacket() instanceof ClientboundSetTimePacket packet)) return;
            long now = System.currentTimeMillis();
            if (lastTimePacketAt != 0L && packet.gameTime() > lastServerGameTime) {
                long elapsed = now - lastTimePacketAt;
                if (elapsed > 0L) {
                    double measured = (packet.gameTime() - lastServerGameTime) * 1000.0 / elapsed;
                    serverTps = Mth.clamp(serverTps * 0.7 + measured * 0.3, 0.0, 20.0);
                }
            }
            lastTimePacketAt = now;
            lastServerGameTime = packet.gameTime();
        }

        @Override
        protected String label() {
            return displayText("display.label", "TPS");
        }

        @Override
        protected String value() {
            return format(serverTps, 1);
        }
    }

    public static final class PingHUD extends InfoHUD {
        public static final PingHUD INSTANCE = new PingHUD();

        private PingHUD() {
            super("Ping HUD", 90f, 20f);
        }

        @Override
        protected String label() {
            return displayText("display.label", "Ping");
        }

        @Override
        protected String value() {
            return ping() + " " + displayText("display.unit", "ms");
        }
    }

    public static final class SpeedHUD extends InfoHUD {
        public static final SpeedHUD INSTANCE = new SpeedHUD();
        private static final Color CONVERTED_SPEED_COLOR = new Color(160, 160, 160, 255);
        private final EnumSetting<SpeedMode> speedMode = enumSetting("Speed Mode", SpeedMode.Horizontal);
        private final BoolSetting showVerticalSpeed = boolSetting("Show Vertical Speed", false);

        private SpeedHUD() {
            super("Speed HUD", 100f, 20f);
        }

        @Override
        protected String label() {
            return displayText("display.label", "Speed");
        }

        @Override
        protected String value() {
            return fullSpeed(speed(speedMode.getValue()));
        }

        @Override
        public void render(DeltaTracker deltaTracker) {
            if (nullCheck()) return;

            TextRenderer renderer = textRendererSupplier.get();
            float textScale = scale.getValue().floatValue();
            float pad = padding.getValue().floatValue();
            float spacing = 2f;
            double speedValue = speed(speedMode.getValue());
            String verticalLabel = displayText("display.vertical label", "Vertical");
            double verticalValue = Math.abs(mc.player.getKnownSpeed().y) * 20.0;
            float mainWidth = lineWidth(renderer, label(), speedValue, textScale);
            float verticalWidth = showVerticalSpeed.getValue()
                    ? lineWidth(renderer, verticalLabel, verticalValue, textScale)
                    : 0f;
            float panelWidth = Math.max(minimumWidth, pad * 2f + Math.max(mainWidth, verticalWidth));
            float lineHeight = renderer.getHeight(textScale);
            float panelHeight = pad * 2f + lineHeight
                    + (showVerticalSpeed.getValue() ? spacing + lineHeight : 0f);
            setBounds(panelWidth, panelHeight);

            drawBackground(panelWidth, panelHeight);
            float y = this.y + pad;
            renderSpeedLine(renderer, label(), speedValue, textScale, y, panelWidth);
            if (showVerticalSpeed.getValue()) {
                renderSpeedLine(renderer, verticalLabel, verticalValue, textScale, y + lineHeight + spacing, panelWidth);
            }
        }

        private float lineWidth(TextRenderer renderer, String lineLabel, double metersPerSecond, float textScale) {
            return renderer.getWidth(lineLabel, textScale) + renderer.getWidth(":", textScale)
                    + renderer.getWidth(" ", textScale) + renderer.getWidth(fullSpeed(metersPerSecond), textScale);
        }

        private void renderSpeedLine(TextRenderer renderer, String lineLabel, double metersPerSecond,
                                     float textScale, float y, float panelWidth) {
            String primaryValue = primarySpeed(metersPerSecond);
            String convertedValue = convertedSpeed(metersPerSecond);
            float labelWidth = renderer.getWidth(lineLabel, textScale);
            float colonWidth = renderer.getWidth(":", textScale);
            float gap = renderer.getWidth(" ", textScale);
            float primaryWidth = renderer.getWidth(primaryValue, textScale);
            float contentWidth = lineWidth(renderer, lineLabel, metersPerSecond, textScale);
            float x = alignedX(panelWidth, contentWidth, padding.getValue().floatValue());
            renderScope().text(lineLabel, x, y, textScale, labelColor.getValue());
            renderScope().text(":", x + labelWidth, y, textScale, labelColor.getValue());
            float valueX = x + labelWidth + colonWidth + gap;
            renderScope().text(primaryValue, valueX, y, textScale, valueColor.getValue());
            renderScope().text(convertedValue, valueX + primaryWidth, y, textScale, CONVERTED_SPEED_COLOR);
        }

        private String primarySpeed(double metersPerSecond) {
            return format(metersPerSecond, 2) + " m/s";
        }

        private String convertedSpeed(double metersPerSecond) {
            return "(" + format(metersPerSecond * 3.6, 2) + " km/h)";
        }

        private String fullSpeed(double metersPerSecond) {
            return primarySpeed(metersPerSecond) + convertedSpeed(metersPerSecond);
        }
    }

    public static final class CoordinatesHUD extends InfoHUD {
        public static final CoordinatesHUD INSTANCE = new CoordinatesHUD();
        private final BoolSetting showChunk = boolSetting("Show Chunk", false);
        private final BoolSetting showRegion = boolSetting("Show Region", false);
        private final BoolSetting oppositeCoordinates = boolSetting("Opposite Coordinates", true);
        private final BoolSetting oppositeChunk = boolSetting("Opposite Chunk", false, oppositeCoordinates::getValue);
        private final BoolSetting oppositeRegion = boolSetting("Opposite Region", false, oppositeCoordinates::getValue);

        private CoordinatesHUD() {
            super("Coordinates HUD", 180f, 66f);
        }

        @Override
        protected String label() {
            return coordinatesLabel();
        }

        @Override
        protected String value() {
            return coordinates(mc.player.blockPosition());
        }

        @Override
        public void render(DeltaTracker deltaTracker) {
            if (nullCheck()) return;
            TextRenderer renderer = textRendererSupplier.get();
            float mainScale = scale.getValue().floatValue();
            float subScale = mainScale * 0.78f;
            float pad = padding.getValue().floatValue();
            float spacing = 2f;
            BlockPos pos = mc.player.blockPosition();
            String mainValue = coordinates(pos);
            // 主世界坐标为绿色、下界为红色、末地为黄色。
            Color mainColor = dimensionColor(mc.level.dimension());
            List<CoordinateLine> lines = new ArrayList<>();
            lines.add(new CoordinateLine(coordinatesLabel(), mainValue, "", mainScale, mainColor));
            if (showChunk.getValue()) lines.add(chunkLine(chunkLabel(), pos, subScale));
            if (showRegion.getValue()) lines.add(regionLine(regionLabel(), pos, subScale));

            CoordinateLine opposite = oppositeLine(pos);
            if (opposite != null) {
                lines.add(opposite);
                BlockPos oppositePos = oppositePosition(pos);
                if (oppositeChunk.getValue()) lines.add(chunkLine(oppositeLabel("Chunk"), oppositePos, subScale));
                if (oppositeRegion.getValue()) lines.add(regionLine(oppositeLabel("Region"), oppositePos, subScale));
            }

            float panelHeight = pad * 2f;
            float width = 0f;
            for (CoordinateLine line : lines) {
                panelHeight += renderer.getHeight(line.scale());
                width = Math.max(width, renderer.getWidth(line.label(), line.scale())
                        + renderer.getWidth(":", line.scale()) + renderer.getWidth(" ", line.scale())
                        + renderer.getWidth(line.value(), line.scale())
                        + renderer.getWidth(line.relativeValue(), line.scale()));
            }
            panelHeight += spacing * Math.max(0, lines.size() - 1);
            float panelWidth = Math.max(minimumWidth, pad * 2f + width);
            setBounds(panelWidth, panelHeight);
            drawBackground(panelWidth, panelHeight);
            float y = this.y + pad;
            for (CoordinateLine line : lines) {
                renderLine(renderer, line, y, panelWidth);
                y += renderer.getHeight(line.scale()) + spacing;
            }
        }

        private void renderLine(TextRenderer renderer, CoordinateLine line, float y, float panelWidth) {
            String lineLabel = line.label();
            String lineValue = line.value();
            float textScale = line.scale();
            float gap = renderer.getWidth(" ", textScale);
            float labelWidth = renderer.getWidth(lineLabel, textScale);
            float colonWidth = renderer.getWidth(":", textScale);
            float valueWidth = renderer.getWidth(lineValue, textScale);
            float contentWidth = labelWidth + colonWidth + gap + valueWidth
                    + renderer.getWidth(line.relativeValue(), textScale);
            float x = alignedX(panelWidth, contentWidth, padding.getValue().floatValue());
            renderScope().text(lineLabel, x, y, textScale, labelColor.getValue());
            renderScope().text(":", x + labelWidth, y, textScale, labelColor.getValue());
            float valueX = x + labelWidth + colonWidth + gap;
            renderScope().text(lineValue, valueX, y, textScale, line.valueColor());
            if (!line.relativeValue().isEmpty()) {
                renderScope().text(line.relativeValue(), valueX + valueWidth, y, textScale, Color.WHITE);
            }
        }

        private String coordinates(BlockPos pos) {
            return pos.getX() + " " + pos.getY() + " " + pos.getZ();
        }

        private String coordinatesLabel() {
            return displayText("display.coordinates", "Coordinates");
        }

        private String chunkLabel() {
            return displayText("display.chunk coordinates", "Chunk Coordinates");
        }

        private String regionLabel() {
            return displayText("display.region coordinates", "Region Coordinates");
        }

        private CoordinateLine chunkLine(String label, BlockPos pos, float textScale) {
            int x = Math.floorDiv(pos.getX(), 16);
            int z = Math.floorDiv(pos.getZ(), 16);
            String value = x + " " + z;
            String relative = " (" + Math.floorMod(pos.getX(), 16) + " " + Math.floorMod(pos.getZ(), 16) + ")";
            return new CoordinateLine(label, value, relative, textScale, valueColor.getValue());
        }

        private CoordinateLine regionLine(String label, BlockPos pos, float textScale) {
            int x = Math.floorDiv(pos.getX(), 512);
            int z = Math.floorDiv(pos.getZ(), 512);
            String value = x + " " + z;
            String relative = " (" + Math.floorMod(pos.getX(), 512) + " " + Math.floorMod(pos.getZ(), 512) + ")";
            return new CoordinateLine(label, value, relative, textScale, valueColor.getValue());
        }

        private CoordinateLine oppositeLine(BlockPos pos) {
            if (!oppositeCoordinates.getValue()) return null;

            double x = pos.getX();
            double z = pos.getZ();
            ResourceKey<Level> oppositeDim;
            if (Level.OVERWORLD.equals(mc.level.dimension())) {
                oppositeDim = Level.NETHER;
                x /= 8.0;
                z /= 8.0;
            } else if (Level.NETHER.equals(mc.level.dimension())) {
                oppositeDim = Level.OVERWORLD;
                x *= 8.0;
                z *= 8.0;
            } else {
                return null;
            }
            return new CoordinateLine(oppositeLabel(""), coordinates(BlockPos.containing(x, pos.getY(), z)), "",
                    scale.getValue().floatValue(), dimensionColor(oppositeDim));
        }

        // 按维度返回坐标颜色：主世界绿、下界红、末地黄（黄白）
        private Color dimensionColor(ResourceKey<Level> dimension) {
            if (Level.NETHER.equals(dimension)) return new Color(255, 95, 95);
            if (Level.END.equals(dimension)) return new Color(255, 235, 95);
            if (Level.OVERWORLD.equals(dimension)) return new Color(95, 230, 130);
            return new Color(255, 255, 255);
        }

        private String oppositeLabel(String kind) {
            String dimension;
            String fallbackDimension;
            if (Level.OVERWORLD.equals(mc.level.dimension())) {
                dimension = "nether";
                fallbackDimension = "Nether";
            } else if (Level.NETHER.equals(mc.level.dimension())) {
                dimension = "overworld";
                fallbackDimension = "Overworld";
            } else {
                dimension = "end";
                fallbackDimension = "End";
            }
            String suffix = kind.isEmpty() ? "Coordinates" : kind + " Coordinates";
            String key = kind.isEmpty()
                    ? "display." + dimension + " coordinates"
                    : "display." + dimension + " " + kind.toLowerCase(Locale.ROOT) + " coordinates";
            return displayText(key,
                    fallbackDimension + " " + suffix);
        }

        private BlockPos oppositePosition(BlockPos pos) {
            double x = pos.getX();
            double z = pos.getZ();
            if (Level.OVERWORLD.equals(mc.level.dimension())) {
                x /= 8.0;
                z /= 8.0;
            } else if (Level.NETHER.equals(mc.level.dimension())) {
                x *= 8.0;
                z *= 8.0;
            }
            return BlockPos.containing(x, pos.getY(), z);
        }

        private record CoordinateLine(String label, String value, String relativeValue, float scale, Color valueColor) {
        }
    }

    public static final class GameModeHUD extends InfoHUD {
        public static final GameModeHUD INSTANCE = new GameModeHUD();

        private GameModeHUD() {
            super("Game Mode HUD", 120f, 20f);
        }

        @Override protected String label() { return displayText("display.label", "Game Mode"); }
        @Override protected String value() { return gameMode(); }
    }

    public static final class LookingAtHUD extends InfoHUD {
        public static final LookingAtHUD INSTANCE = new LookingAtHUD();
        private final ColorSetting targetColor = colorSetting("Target Color", new Color(95, 230, 130, 255));

        private LookingAtHUD() { super("Looking At HUD", 150f, 20f); }
        @Override protected String label() { return displayText("display.label", "Looking At"); }
        @Override protected String value() { return target().description(); }

        @Override
        public void render(DeltaTracker deltaTracker) {
            if (nullCheck()) return;

            LookingAtTarget target = target();
            TextRenderer renderer = textRendererSupplier.get();
            float textScale = scale.getValue().floatValue();
            float pad = padding.getValue().floatValue();
            float gap = renderer.getWidth(" ", textScale);
            float labelWidth = renderer.getWidth(label(), textScale);
            float colonWidth = renderer.getWidth(":", textScale);
            boolean hasTarget = !target.description().isEmpty();
            float targetWidth = renderer.getWidth(target.description(), textScale);
            float coordinatesWidth = renderer.getWidth(target.coordinates(), textScale);
            float firstColonWidth = hasTarget ? colonWidth + gap : 0f;
            float secondColonWidth = hasTarget ? colonWidth + gap : 0f;
            float contentWidth = labelWidth + firstColonWidth + targetWidth + secondColonWidth + coordinatesWidth;
            float panelWidth = Math.max(minimumWidth, pad * 2f + contentWidth);
            float panelHeight = pad * 2f + renderer.getHeight(textScale);
            setBounds(panelWidth, panelHeight);

            drawBackground(panelWidth, panelHeight);
            float x = alignedX(panelWidth, contentWidth, pad);
            float y = this.y + pad;
            renderScope().text(label(), x, y, textScale, labelColor.getValue());
            if (hasTarget) {
                x += labelWidth;
                renderScope().text(":", x, y, textScale, labelColor.getValue());
                x += colonWidth + gap;
                renderScope().text(target.description(), x, y, textScale, targetColor.getValue());
                x += targetWidth;
                renderScope().text(":", x, y, textScale, labelColor.getValue());
                x += colonWidth + gap;
                renderScope().text(target.coordinates(), x, y, textScale, valueColor.getValue());
            }
        }

        private LookingAtTarget target() {
            HitResult hit = mc.hitResult;
            if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = blockHit.getBlockPos();
                if (mc.level.getBlockState(pos).isAir()) return LookingAtTarget.EMPTY;
                String kind = displayText("display.block", "Block");
                String name = mc.level.getBlockState(pos).getBlock().getName().getString();
                return new LookingAtTarget(kind + " (" + name + ")", coordinates(pos));
            }
            if (hit instanceof EntityHitResult entityHit) {
                BlockPos pos = entityHit.getEntity().blockPosition();
                String kind = displayText("display.entity", "Entity");
                String name = entityHit.getEntity().getName().getString();
                return new LookingAtTarget(kind + " (" + name + ")", coordinates(pos));
            }
            return LookingAtTarget.EMPTY;
        }

        private String coordinates(BlockPos pos) {
            return pos.getX() + " " + pos.getY() + " " + pos.getZ();
        }

        private record LookingAtTarget(String description, String coordinates) {
            private static final LookingAtTarget EMPTY = new LookingAtTarget("", "");
        }
    }

    public static final class BreakingProgressHUD extends InfoHUD {
        public static final BreakingProgressHUD INSTANCE = new BreakingProgressHUD();

        private BreakingProgressHUD() { super("Breaking Progress HUD", 150f, 20f); }
        @Override protected String label() { return displayText("display.label", "Breaking"); }
        @Override protected String value() { return breakingProgress(); }
    }

    public static final class ServerHUD extends InfoHUD {
        public static final ServerHUD INSTANCE = new ServerHUD();

        private ServerHUD() { super("Server HUD", 150f, 20f); }
        @Override protected String label() { return displayText("display.label", "Server"); }
        @Override protected String value() { return server(); }
    }

    public static final class WeatherHUD extends InfoHUD {
        public static final WeatherHUD INSTANCE = new WeatherHUD();

        private WeatherHUD() { super("Weather HUD", 110f, 20f); }
        @Override protected String label() { return displayText("display.label", "Weather"); }
        @Override protected String value() { return weather(); }
    }

    public static final class BiomeHUD extends InfoHUD {
        public static final BiomeHUD INSTANCE = new BiomeHUD();

        private BiomeHUD() { super("Biome HUD", 140f, 20f); }
        @Override protected String label() { return displayText("display.label", "Biome"); }
        @Override protected String value() { return biome(); }
    }

    public static final class WorldTimeHUD extends InfoHUD {
        public static final WorldTimeHUD INSTANCE = new WorldTimeHUD();

        private WorldTimeHUD() { super("World Time HUD", 130f, 20f); }
        @Override protected String label() { return displayText("display.label", "World Time"); }
        @Override protected String value() { return worldTime(); }
    }

    public static final class RealTimeHUD extends InfoHUD {
        public static final RealTimeHUD INSTANCE = new RealTimeHUD();
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        private RealTimeHUD() { super("Real Time HUD", 120f, 20f); }
        @Override protected String label() { return displayText("display.label", "Real Time"); }
        @Override protected String value() { return LocalDateTime.now().format(FORMATTER); }
    }

    public static final class RotationHUD extends InfoHUD {
        public static final RotationHUD INSTANCE = new RotationHUD();

        private RotationHUD() { super("Rotation HUD", 140f, 20f); }
        @Override protected String label() { return displayText("display.label", "Rotation"); }
        @Override protected String value() { return rotation(); }
    }
}
