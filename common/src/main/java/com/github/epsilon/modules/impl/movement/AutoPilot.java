package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.EntityMoveEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.movement.elytrafly.ElytraFlightModes;
import com.github.epsilon.modules.impl.movement.elytrafly.ElytraFly;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ButtonSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.settings.impl.StringSetting;
import com.github.epsilon.utils.movement.AutoPilotUtil;
import net.minecraft.world.phys.Vec3;

public class AutoPilot extends Module {

    public static final AutoPilot INSTANCE = new AutoPilot();

    private enum Mode {
        Simple
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Simple);
    private final IntSetting cruiseHeight = intSetting("Cruise Height", 320, -1000, 4000, 1);
    private final StringSetting destinationX = stringSetting("Destination X", "0");
    private final StringSetting destinationZ = stringSetting("Destination Z", "0");
    private final ButtonSetting resetDestination = buttonSetting("Reset Destination", () -> {
        destinationX.setValue("0");
        destinationZ.setValue("0");
    });
    private final ButtonSetting pasteCoordinates = buttonSetting("Paste Coords", () -> {
        String clipboard = AutoPilotUtil.getClipboardText();
        if (clipboard == null || clipboard.isEmpty()) return;

        double[] coordinates = AutoPilotUtil.parseCoordinates(clipboard);
        if (coordinates == null) return;

        destinationX.setValue(String.valueOf((int) coordinates[0]));
        destinationZ.setValue(String.valueOf((int) coordinates[1]));
    });
    private final BoolSetting toggleOffOnArrival = boolSetting("Toggle Off On Arrival", true);
    private final BoolSetting pauseInUnloadedChunks = boolSetting("Pause In Unloaded Chunks", false);
    private final BoolSetting playerDodge = boolSetting("Player Dodge", false);

    private boolean warnedWrongFlightMode;

    private AutoPilot() {
        super("AutoPilot", Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        warnedWrongFlightMode = false;
    }

    @Override
    protected void onDisable() {
        warnedWrongFlightMode = false;
    }

    public float getCruiseYaw() {
        if (!isEnabled()) return AutoPilotUtil.INACTIVE_YAW;
        if (!isNcpControlAvailable()) {
            warnWrongFlightMode();
            return AutoPilotUtil.INACTIVE_YAW;
        }
        // 鞘翅巡航只允许在实际滑翔时生效，乘坐实体时不向鞘翅控制注入航向。
        if (mc.player == null || mc.player.isPassenger() || !mc.player.isFallFlying()) {
            return AutoPilotUtil.INACTIVE_YAW;
        }
        return AutoPilotUtil.calcAutoMoveYaw(
                destinationX.getValue(),
                destinationZ.getValue(),
                cruiseHeight.getValue(),
                playerDodge.getValue()
        );
    }

    private boolean isNcpControlAvailable() {
        return ElytraFly.INSTANCE.isEnabled() && ElytraFly.INSTANCE.mode.is(ElytraFlightModes.NCPControl);
    }

    private void warnWrongFlightMode() {
        if (warnedWrongFlightMode) return;
        warnedWrongFlightMode = true;
        Managers.NOTIFICATION.error(getTranslatedName(), EpsilonTranslations.AutoPilot.WRONG_FLIGHT_MODE.getTranslatedName());
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onEntityMove(EntityMoveEvent event) {
        if (nullCheck()) return;

        EntityControl entityControl = EntityControl.INSTANCE;
        if (!entityControl.canControl(event.entity)) return;

        switch (mode.getValue()) {
            case Simple -> runSimpleMode(event, entityControl);
        }
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (nullCheck() || !ElytraFly.INSTANCE.isEnabled() || !mc.player.isFallFlying()) return;
        if (ElytraFly.INSTANCE.mode.is(ElytraFlightModes.NCPControl)) {
            warnedWrongFlightMode = false;
        } else {
            warnWrongFlightMode();
        }
    }

    private void runSimpleMode(EntityMoveEvent event, EntityControl entityControl) {
        float autoYaw = AutoPilotUtil.calcAutoMoveYaw(
                destinationX.getValue(),
                destinationZ.getValue(),
                cruiseHeight.getValue(),
                playerDodge.getValue()
        );
        if (autoYaw != AutoPilotUtil.INACTIVE_YAW) {
            event.movement = getAutoPilotMovement(autoYaw, entityControl.getHorizontalSpeed());
            return;
        }

        tryToggleOffOnArrival();
    }

    private Vec3 getAutoPilotMovement(float autoYaw, double horizontalSpeed) {
        double speed = horizontalSpeed / 20.0;
        double radians = Math.toRadians(autoYaw + 90.0);
        double motionX = Math.cos(radians) * speed;
        double motionZ = Math.sin(radians) * speed;

        if (!pauseInUnloadedChunks.getValue()) {
            return new Vec3(motionX, 0.0, motionZ);
        }

        int chunkX = (int) (mc.player.getX() / 16);
        int chunkZ = (int) (mc.player.getZ() / 16);
        if (!mc.level.getChunkSource().hasChunk(chunkX, chunkZ)) {
            return Vec3.ZERO;
        }
        return new Vec3(motionX, 0.0, motionZ);
    }

    private void tryToggleOffOnArrival() {
        if (!toggleOffOnArrival.getValue()) return;

        try {
            double targetX = Double.parseDouble(destinationX.getValue());
            double targetZ = Double.parseDouble(destinationZ.getValue());
            double distanceX = mc.player.getX() - targetX;
            double distanceZ = mc.player.getZ() - targetZ;
            if (Math.sqrt(distanceX * distanceX + distanceZ * distanceZ) <= 40.0) {
                setEnabled(false);
            }
        } catch (NumberFormatException ignored) {
        }
    }
}
