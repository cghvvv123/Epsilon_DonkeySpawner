package com.github.epsilon.modules.impl.movement.elytrafly;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import org.lwjgl.glfw.GLFW;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.Map;

public class ElytraFly extends Module {

    public static final ElytraFly INSTANCE = new ElytraFly();

    private ElytraFly() {
        super("Elytra Fly", Category.MOVEMENT);
        modes.put(ElytraFlightModes.Control, new ControlElytraFlightMode(this));
        modes.put(ElytraFlightModes.Pitch40, new Pitch40ElytraFlightMode(this));
        modes.put(ElytraFlightModes.NCPControl, new NCPControlElytraFlightMode(this));
    }

    public enum SwapMode {
        Silent,
        InvSwitch
    }

    public record Pitch40ControlState(
            boolean enabled,
            ElytraFlightModes mode,
            double lowerBounds,
            boolean autoTakeoff,
            double takeoffTargetHeight,
            boolean autoFirework,
            Float yawOverride
    ) {
    }

    private final Map<ElytraFlightModes, ElytraFlightMode> modes = new EnumMap<>(ElytraFlightModes.class);

    public final EnumSetting<ElytraFlightModes> mode = enumSetting("Mode", ElytraFlightModes.Control, this::onModeChanged);
    public final EnumSetting<SwapMode> swapMode = enumSetting("Swap Mode", SwapMode.InvSwitch);

    public final BoolSetting armored = boolSetting("Armored", false,
            () -> mode.is(ElytraFlightModes.Control) || mode.is(ElytraFlightModes.Pitch40));
    public final BoolSetting unbreaking = boolSetting("Unbreaking", true);
    public final IntSetting unbreakingDelay = intSetting("Unbreaking Delay", 800, 100, 2000, 50, () -> unbreaking.getValue());
    public final BoolSetting unbreakingInGui = boolSetting("Unbreaking In GUI", true,
            () -> unbreaking.getValue() && mode.is(ElytraFlightModes.NCPControl));

    public final BoolSetting smartInfElytra = boolSetting("Smart Inf Elytra", true,
            () -> unbreaking.getValue() && mode.is(ElytraFlightModes.NCPControl));
    public final BoolSetting smartInfOnlyWhenAfk = boolSetting("Only When AFK", true, this::isSmartInfEnabled);
    public final IntSetting smartInfDirectionTime = intSetting("Direction Time", 60, 0, 200, 1,
            () -> isSmartInfEnabled() && smartInfOnlyWhenAfk.getValue());
    public final BoolSetting smartInfOnlyWhenSteady = boolSetting("Only When Steady", false,
            () -> isSmartInfEnabled() && smartInfOnlyWhenAfk.getValue());
    public final DoubleSetting smartInfDirectionTolerance = doubleSetting("Direction Tolerance", 5.0, 0.0, 180.0, 1.0,
            () -> isSmartInfEnabled() && smartInfOnlyWhenAfk.getValue() && !smartInfOnlyWhenSteady.getValue());
    public final DoubleSetting smartInfHorizontalTolerance = doubleSetting("Horizontal Tolerance", 0.1, 0.0, 1.0, 0.01,
            () -> isSmartInfEnabled() && smartInfOnlyWhenAfk.getValue());
    public final DoubleSetting smartInfVerticalTolerance = doubleSetting("Vertical Tolerance", 0.2, 0.0, 1.0, 0.01,
            () -> isSmartInfEnabled() && smartInfOnlyWhenAfk.getValue());
    public final BoolSetting smartInfExcludeDescending = boolSetting("Exclude Descending", false, this::isSmartInfEnabled);
    public final BoolSetting smartInfOnlyWhenAboveClear = boolSetting("Only When Above Clear", false,
            () -> isSmartInfEnabled() && !smartInfOnlyWhenSteady.getValue());
    public final IntSetting smartInfAboveClearTolerance = intSetting("Above Clear Tolerance", 2, 0, 5, 1,
            () -> isSmartInfEnabled() && smartInfOnlyWhenAboveClear.getValue() && !smartInfOnlyWhenSteady.getValue());
    public final IntSetting smartInfBelowClearTolerance = intSetting("Below Clear Tolerance", 2, 0, 5, 1,
            () -> isSmartInfEnabled() && smartInfOnlyWhenAboveClear.getValue() && !smartInfOnlyWhenSteady.getValue());
    public final BoolSetting noSprint = boolSetting("No Sprint", true, () -> mode.is(ElytraFlightModes.Control) && armored.getValue());
    public final BoolSetting useFireworks = boolSetting("Use Fireworks", true, () -> mode.is(ElytraFlightModes.Control) || mode.is(ElytraFlightModes.NCPControl));
    public final IntSetting boostDelay = intSetting("Boost Delay", 20, 2, 50, 1, () -> (mode.is(ElytraFlightModes.Control) || mode.is(ElytraFlightModes.NCPControl)) && useFireworks.getValue());
    public final DoubleSetting verticalMultiple = doubleSetting("Vertical Multiple", 0.55, 0.0, 20.0, 0.01,
            () -> mode.is(ElytraFlightModes.NCPControl));
    public final DoubleSetting verticalMaxSpeed = doubleSetting("Vertical Max Speed", 1.537, 0.0, 20.0, 0.001,
            () -> mode.is(ElytraFlightModes.NCPControl));
    public final BoolSetting checkFeet = boolSetting("Check Feet", true,
            () -> mode.is(ElytraFlightModes.NCPControl));
    public final IntSetting checkFeetHeight = intSetting("Check Feet Height", 2, 0, 10, 1,
            () -> mode.is(ElytraFlightModes.NCPControl) && checkFeet.getValue());

    public final DoubleSetting pitch40lowerBounds = doubleSetting("Pitch40 Lower Bounds", 180.0, -128.0, 1024.0, 1.0, () -> mode.is(ElytraFlightModes.Pitch40));
    public final DoubleSetting pitch40rotationSpeedUp = doubleSetting("Pitch40 Rotate Speed Up", 5.45, 1.0, 20.0, 0.05, () -> mode.is(ElytraFlightModes.Pitch40));
    public final DoubleSetting pitch40rotationSpeedDown = doubleSetting("Pitch40 Rotate Speed Down", 0.90, 0.5, 2.0, 0.05, () -> mode.is(ElytraFlightModes.Pitch40));
    public final IntSetting pitch40PacketDelay = intSetting("Pitch40 Packet Delay", 3, 1, 20, 1, () -> mode.is(ElytraFlightModes.Pitch40) && armored.getValue());
    public final BoolSetting pitch40AutoTakeoff = boolSetting("Pitch40 Auto Takeoff", true, () -> mode.is(ElytraFlightModes.Pitch40));
    public final DoubleSetting pitch40TakeoffTargetHeight = doubleSetting("Pitch40 Takeoff Target Height", 300.0, -128.0, 1024.0, 1.0, () -> mode.is(ElytraFlightModes.Pitch40) && pitch40AutoTakeoff.getValue());
    public final BoolSetting pitch40AutoFirework = boolSetting("Pitch40 Auto Firework", true, () -> mode.is(ElytraFlightModes.Pitch40) && pitch40AutoTakeoff.getValue());
    public final IntSetting pitch40FireworkCooldown = intSetting("Pitch40 Firework Cooldown", 10, 0, 100, 1, () -> mode.is(ElytraFlightModes.Pitch40) && pitch40AutoTakeoff.getValue() && pitch40AutoFirework.getValue());

    private ElytraFlightModes activeModeType;
    private Float pitch40YawOverride;
    private boolean forcePause;
    private int smartInfStableTicks;
    private float smartInfLastDirection = Float.NaN;
    private boolean smartInfReady = true;

    @Override
    protected void onEnable() {
        activeModeType = mode.getValue();
        resetSmartInfState();
        getActiveMode().armUnbreakingTimer();
        getActiveMode().onEnable();
    }

    @Override
    protected void onDisable() {
        getMode(activeModeType).onDisable();
        forcePause = false;
        resetSmartInfState();
    }

    @Override
    public String getInfo() {
        return mode.getValue().toString();
    }

    public boolean isArmorMode() {
        return isEnabled() && mode.is(ElytraFlightModes.Control) && armored.getValue();
    }

    public Pitch40ControlState capturePitch40ControlState() {
        return new Pitch40ControlState(
                isEnabled(),
                mode.getValue(),
                pitch40lowerBounds.getValue(),
                pitch40AutoTakeoff.getValue(),
                pitch40TakeoffTargetHeight.getValue(),
                pitch40AutoFirework.getValue(),
                pitch40YawOverride
        );
    }

    public void applyPitch40Control(boolean autoTakeoff, boolean autoFirework, double lowerBounds, double takeoffTargetHeight) {
        applyPitch40Control(autoTakeoff, autoFirework, lowerBounds, takeoffTargetHeight, null);
    }

    public void applyPitch40Control(boolean autoTakeoff, boolean autoFirework, double lowerBounds, double takeoffTargetHeight, Float yawOverride) {
        pitch40AutoTakeoff.setValue(autoTakeoff);
        pitch40AutoFirework.setValue(autoFirework);
        pitch40lowerBounds.setValue(lowerBounds);
        pitch40TakeoffTargetHeight.setValue(takeoffTargetHeight);
        pitch40YawOverride = yawOverride;

        if (!mode.is(ElytraFlightModes.Pitch40)) {
            mode.setMode(ElytraFlightModes.Pitch40);
        }
        if (!isEnabled()) {
            setEnabled(true);
        }
    }

    public void restorePitch40Control(Pitch40ControlState state) {
        if (state == null) return;

        pitch40lowerBounds.setValue(state.lowerBounds());
        pitch40AutoTakeoff.setValue(state.autoTakeoff());
        pitch40TakeoffTargetHeight.setValue(state.takeoffTargetHeight());
        pitch40AutoFirework.setValue(state.autoFirework());
        pitch40YawOverride = state.yawOverride();
        mode.setMode(state.mode());

        if (!state.enabled() && isEnabled()) {
            setEnabled(false);
        }
    }

    public float getPitch40Yaw(float fallback) {
        return pitch40YawOverride != null ? pitch40YawOverride : fallback;
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) return;
        updateSmartInfState();
        if (forcePause) return;
        getActiveMode().onPlayerTick();
        if (isEnabled()) {
            getActiveMode().handleUnbreaking();
        }
    }

    @EventHandler
    private void onClientTick(ClientTickEvent.Pre event) {
        if (nullCheck() || forcePause) return;
        getActiveMode().onClientTick();
    }

    @EventHandler
    private void onTravel(TravelEvent event) {
        if (nullCheck()) return;
        if (forcePause) return;
        getActiveMode().onTravel(event);
    }

    @EventHandler
    private void onLivingEntityTravel(LivingEntityTravelEvent event) {
        if (nullCheck() || forcePause) return;
        getActiveMode().onLivingEntityTravel(event);
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (nullCheck()) return;
        if (forcePause) return;
        getActiveMode().onKeyboardInput(event);
    }

    @EventHandler
    private void onFallFlying(FallFlyingEvent event) {
        if (nullCheck()) return;
        if (forcePause) return;
        getActiveMode().onFallFlying(event);
    }

    @EventHandler
    private void onFireworkRotationUpdate(FireworkRotationEvent event) {
        if (nullCheck()) return;
        if (forcePause) return;
        getActiveMode().onFireworkUpdate(event);
    }

    @EventHandler
    private void onMousePress(MousePressEvent event) {
        if (mc.screen != null) return;
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && event.getAction() == GLFW.GLFW_PRESS && getActiveMode().shouldCancelRightClick()) {
            event.cancel();
        }
    }

    public ElytraFlightMode getActiveMode() {
        return getMode(mode.getValue());
    }

    public void setForcePause(boolean forcePause) {
        this.forcePause = forcePause;
    }

    public boolean isForcePaused() {
        return forcePause;
    }

    public void resetSmartInfForEmergency() {
        resetSmartInfState();
    }

    private boolean isSmartInfEnabled() {
        return mode.is(ElytraFlightModes.NCPControl)
                && unbreaking.getValue()
                && smartInfElytra.getValue();
    }

    public boolean shouldResetUnbreaking() {
        return !isSmartInfEnabled() || smartInfReady;
    }

    public boolean isSmartInfResetAreaClear() {
        return !isSmartInfEnabled()
                || !smartInfOnlyWhenAboveClear.getValue()
                || isSmartInfAreaClear();
    }

    private void resetSmartInfState() {
        smartInfStableTicks = 0;
        smartInfLastDirection = Float.NaN;
        smartInfReady = true;
    }

    private void updateSmartInfState() {
        if (!isSmartInfEnabled() || !isEnabled()) {
            resetSmartInfState();
            return;
        }
        // 无限耐久换鞘翅时会短暂失去 FallFlying 标记，不能把这次内部换装当成重新起算。
        if (!mc.player.isFallFlying()) {
            if (mc.player.onGround() || mc.player.isPassenger()) resetSmartInfState();
            return;
        }
        boolean wasReady = smartInfReady;
        if (!smartInfOnlyWhenAfk.getValue()) {
            smartInfReady = !smartInfExcludeDescending.getValue()
                    || mc.player.getDeltaMovement().y >= -smartInfVerticalTolerance.getValue();
            if (!wasReady && smartInfReady) getActiveMode().resetUnbreakingTimer();
            return;
        }

        Vec3 velocity = mc.player.getDeltaMovement();
        double horizontalSpeedSquared = velocity.x * velocity.x + velocity.z * velocity.z;
        boolean steady = horizontalSpeedSquared < smartInfHorizontalTolerance.getValue() * smartInfHorizontalTolerance.getValue()
                && Math.abs(velocity.y) < smartInfVerticalTolerance.getValue();
        boolean meetsCondition = steady;

        if (!steady && !smartInfOnlyWhenSteady.getValue()) {
            if (smartInfExcludeDescending.getValue() && velocity.y < -smartInfVerticalTolerance.getValue()) {
                meetsCondition = false;
            } else {
                float direction = (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
                meetsCondition = !Float.isNaN(smartInfLastDirection)
                        && angleDifference(direction, smartInfLastDirection) <= smartInfDirectionTolerance.getValue();
                smartInfLastDirection = direction;
            }
        }

        if (meetsCondition) smartInfStableTicks++;
        else smartInfStableTicks = 0;

        smartInfReady = smartInfStableTicks >= smartInfDirectionTime.getValue();
        if (!wasReady && smartInfReady) getActiveMode().resetUnbreakingTimer();
    }

    private float angleDifference(float first, float second) {
        float difference = Math.abs(first - second) % 360.0F;
        return difference > 180.0F ? 360.0F - difference : difference;
    }

    private boolean isSmartInfAreaClear() {
        BlockPos base = mc.player.blockPosition();
        for (int y = 1; y <= smartInfAboveClearTolerance.getValue(); y++) {
            BlockPos pos = base.above(y);
            if (mc.level.getBlockState(pos).isCollisionShapeFullBlock(mc.level, pos)) return false;
        }
        for (int y = 1; y <= smartInfBelowClearTolerance.getValue(); y++) {
            BlockPos pos = base.below(y);
            if (mc.level.getBlockState(pos).isCollisionShapeFullBlock(mc.level, pos)) return false;
        }
        return true;
    }

    private ElytraFlightMode getMode(ElytraFlightModes mode) {
        return modes.getOrDefault(mode, modes.get(ElytraFlightModes.Control));
    }

    private void onModeChanged(ElytraFlightModes newMode) {
        if (!isEnabled()) {
            activeModeType = newMode;
            return;
        }

        getMode(activeModeType).onDisable();
        activeModeType = newMode;
        getActiveMode().onEnable();
    }

}
