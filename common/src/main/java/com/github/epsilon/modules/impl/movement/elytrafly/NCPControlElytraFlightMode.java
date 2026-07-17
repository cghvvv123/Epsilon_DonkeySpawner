package com.github.epsilon.modules.impl.movement.elytrafly;

import com.github.epsilon.events.impl.TravelEvent;
import com.github.epsilon.modules.impl.movement.AutoPilot;
import com.github.epsilon.modules.impl.player.ElytraSwap;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.utils.movement.AutoPilotUtil;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.Vec2;

public final class NCPControlElytraFlightMode extends ElytraFlightMode {

    private final TimerUtils fireworkTimer = new TimerUtils();

    private final BoolSetting easyTakeoff = elytraFly.boolSetting("Easy Takeoff", true, ncpVisible());
    private final DoubleSetting yawVelocity = elytraFly.doubleSetting("Yaw Velocity", 180.0, -270.0, 270.0, 1.0, ncpVisible());

    private final SettingGroup speedGroup = elytraFly.settingGroup("Speed");
    private final SettingGroup waterSpeedGroup = elytraFly.settingGroup("Water Speed");
    private final SettingGroup risePitchGroup = elytraFly.settingGroup("Rise Pitch");
    private final SettingGroup potionGroup = elytraFly.settingGroup("Potion Handler");

    private final DoubleSetting speed = speedSetting("Speed", 4.296, speedGroup);
    private final DoubleSetting upSpeed = speedSetting("Up Speed", 3.0, speedGroup);
    private final DoubleSetting downSpeed = speedSetting("Down Speed", 3.0, speedGroup);

    private final DoubleSetting waterSpeed = speedSetting("Water Speed", 3.0, waterSpeedGroup);
    private final DoubleSetting waterUpSpeed = speedSetting("Water Up Speed", 3.0, waterSpeedGroup);
    private final DoubleSetting waterDownSpeed = speedSetting("Water Down Speed", 3.0, waterSpeedGroup);
    private final DoubleSetting waterYawSpeedOffset = elytraFly.doubleSetting("Water Yaw Speed Offset", 0.8, 0.0, 40.0, 0.1, ncpVisible()).group(waterSpeedGroup);

    private final DoubleSetting risePitch = elytraFly.doubleSetting("Rise Pitch", 60.0, 0.0, 90.0, 1.0, ncpVisible()).group(risePitchGroup);
    private final DoubleSetting riseMinSpeed = elytraFly.doubleSetting("Rise Min Speed", 0.5, 0.0, 10.0, 0.01, ncpVisible()).group(risePitchGroup);
    private final DoubleSetting waterRisePitch = elytraFly.doubleSetting("Water Rise Pitch", 45.0, 0.0, 90.0, 1.0, ncpVisible()).group(risePitchGroup);
    private final DoubleSetting waterRiseMinSpeed = elytraFly.doubleSetting("Water Rise Min Speed", 0.417, 0.0, 10.0, 0.01, ncpVisible()).group(risePitchGroup);

    private final BoolSetting potionSpeed = elytraFly.boolSetting("Potion Speed", false, ncpVisible()).group(potionGroup);
    private final DoubleSetting speedPotionMultiplier = elytraFly.doubleSetting("Speed Potion Multiplier", 0.405, 0.0, 2.0, 0.001,
            () -> potionSpeed.getValue() && elytraFly.mode.is(ElytraFlightModes.NCPControl)).group(potionGroup);
    private final BoolSetting potionSlowness = elytraFly.boolSetting("Slowness Potion", true,
            () -> potionSpeed.getValue() && elytraFly.mode.is(ElytraFlightModes.NCPControl)).group(potionGroup);
    private final DoubleSetting slownessPotionMultiplier = elytraFly.doubleSetting("Slowness Potion Multiplier", 0.975, 0.0, 2.0, 0.001,
            () -> potionSpeed.getValue() && potionSlowness.getValue() && elytraFly.mode.is(ElytraFlightModes.NCPControl)).group(potionGroup);

    private float waterYawOffset;
    private int waterYawDirection = 1;
    /** 源 ElytraFly 的 offsetYaw：无水平输入上升时只在低速阶段推进。 */
    private float riseYaw;
    private int waterYawTick = Integer.MIN_VALUE;
    private int yawVelocityTick = Integer.MIN_VALUE;
    private Double originalGravity;
    private boolean unbreakingGravityLock;
    private long lastEasyTakeoffAttempt;
    private Vec3 vec = Vec3.ZERO;
    private Vec3 oVec = Vec3.ZERO;

    public NCPControlElytraFlightMode(ElytraFly elytraFly) {
        super(elytraFly);
    }

    private DoubleSetting speedSetting(String name, double value, SettingGroup group) {
        return elytraFly.doubleSetting(name, value, 0.0, 100.0, 0.001, ncpVisible()).group(group);
    }

    private Setting.Dependency ncpVisible() {
        return () -> elytraFly.mode.is(ElytraFlightModes.NCPControl);
    }

    @Override
    public void onEnable() {
        fireworkTimer.setMs(917813L);
        unbreakingGravityLock = false;
        originalGravity = mc.player == null || mc.player.getAttribute(Attributes.GRAVITY) == null
                ? null : mc.player.getAttribute(Attributes.GRAVITY).getBaseValue();
    }

    @Override
    public void onDisable() {
        if (unbreakingGravityLock) restoreGravity();
    }

    @Override
    public void onPlayerTick() {
        if (mc.player == null) return;

        restoreUnbreakingGravity();
        handleEasyTakeoff();
        if (mc.player.isFallFlying()) updateDefensiveGravity();
    }

    @Override
    public void onTravel(TravelEvent event) {
        updatePositionDelta();

        if (!mc.player.isFallFlying()) return;
        event.cancel();

        Vec3 motion = computeMotion();
        movePlayer(motion);
    }

    @Override
    public boolean shouldCancelRightClick() {
        return false;
    }

    private void restoreUnbreakingGravity() {
        if (!unbreakingGravityLock) return;
        restoreGravity();
        unbreakingGravityLock = false;
    }

    private void handleEasyTakeoff() {
        if (!easyTakeoff.getValue()
                || mc.player.isFallFlying()
                || !mc.player.getItemBySlot(EquipmentSlot.CHEST).has(DataComponents.GLIDER)) return;

        long now = System.currentTimeMillis();
        if (now - lastEasyTakeoffAttempt <= 500L
                || mc.player.onGround()
                || mc.player.isPassenger()) return;

        lastEasyTakeoffAttempt = now;
        mc.player.startFallFlying();
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(
                    mc.player,
                    ServerboundPlayerCommandPacket.Action.START_FALL_FLYING
            ));
        }
    }

    private void updatePositionDelta() {
        oVec = vec;
        vec = mc.player.position();
    }

    private void movePlayer(Vec3 motion) {
        mc.player.move(MoverType.SELF, motion);
        mc.player.setDeltaMovement(motion);
        mc.player.hurtMarked = true;
    }

    public void lockGravityForUnbreaking() {
        if (mc.player == null) return;
        var gravity = mc.player.getAttribute(Attributes.GRAVITY);
        if (gravity == null) return;
        if (originalGravity == null) originalGravity = gravity.getBaseValue();
        gravity.setBaseValue(0.0D);
        unbreakingGravityLock = true;
    }

    private Vec3 computeMotion() {
        boolean water = mc.player.isInLiquid();
        boolean jump = mc.options.keyJump.isDown() && !mc.options.keyShift.isDown();
        boolean sneak = mc.options.keyShift.isDown() && !mc.options.keyJump.isDown();
        float yaw = mc.player.getYRot();

        boolean autoMove = false;
        if (isMoveBindPress()) {
            yawVelocityTick = Integer.MIN_VALUE;
        } else if (jump) {
            yaw = riseYaw;
        } else if (!sneak) {
            float cruiseYaw = AutoPilot.INSTANCE.getCruiseYaw();
            if (cruiseYaw != AutoPilotUtil.INACTIVE_YAW) {
                yaw = cruiseYaw;
                autoMove = true;
            }
        }

        if (water) {
            refreshWaterYawOffset();
            yaw += waterYawOffset;
        }

        if (jump) {
            return riseHeight(water, yaw);
        }
        if (sneak) {
            return move(yaw, false, water ? waterDownSpeed.getValue() : downSpeed.getValue(), 0.0D,
                    potionMultiplier(false)).add(downMove(water));
        }
        return move(yaw, autoMove, water ? waterSpeed.getValue() : speed.getValue(), 0.0D,
                potionMultiplier(false));
    }

    private Vec3 riseHeight(boolean water, float yaw) {
        boolean boosting = isBoosting();
        boolean hasFirework = elytraFly.useFireworks.getValue() && hasUsableFirework();
        boolean verticalTakeoffClear = !elytraFly.checkFeet.getValue()
                || isVerticalClear(elytraFly.checkFeetHeight.getValue());
        if ((boosting || hasFirework) && verticalTakeoffClear) {
            if (!boosting) handleRiseFirework();
            return upMove().add(move(yaw, false, water ? waterUpSpeed.getValue() : upSpeed.getValue(),
                    0.0D, potionMultiplier(true)));
        }

        double horizontal = water ? waterUpSpeed.getValue() : upSpeed.getValue();
        double minSpeed = water ? waterRiseMinSpeed.getValue() : riseMinSpeed.getValue();
        if (getSpeed().horizontalDistance() >= minSpeed) {
            return doNormalFly(water ? waterRisePitch.getValue() : risePitch.getValue(), yaw);
        }
        changeRiseYaw();
        if (!isMoveBindPress()) yaw = riseYaw;
        return move(yaw, !isMoveBindPress(), horizontal, 0.0D, potionMultiplier(true));
    }

    private Vec3 move(float yaw, boolean autoMove, double horizontal, double vertical, double multiplier) {
        Vec2 moveVector = mc.player.input.getMoveVector();
        float forward = moveVector.y;
        float strafe = moveVector.x;
        if (autoMove) {
            double radians = Math.toRadians(yaw + 90.0F);
            return new Vec3(Math.sin(radians) * horizontal * multiplier, vertical * multiplier,
                    Math.cos(radians) * horizontal * multiplier);
        }
        if (forward == 0.0F && strafe == 0.0F) return new Vec3(0.0D, vertical * multiplier, 0.0D);
        if (forward != 0.0F) {
            if (strafe >= 1.0F) {
                yaw += forward > 0.0F ? -45.0F : 45.0F;
                strafe = 0.0F;
            } else if (strafe <= -1.0F) {
                yaw += forward > 0.0F ? 45.0F : -45.0F;
                strafe = 0.0F;
            }
            forward = forward > 0.0F ? 1.0F : -1.0F;
        }
        double radians = Math.toRadians(yaw + 90.0F);
        double x = Math.cos(radians) * forward * horizontal + Math.sin(radians) * strafe * horizontal;
        double z = Math.sin(radians) * forward * horizontal - Math.cos(radians) * strafe * horizontal;
        return new Vec3(x * multiplier, vertical * multiplier, z * multiplier);
    }

    private Vec3 downMove(boolean water) {
        if (!mc.options.keyShift.isDown()) return Vec3.ZERO;
        double speed = water ? waterDownSpeed.getValue() : downSpeed.getValue();
        return new Vec3(0.0D, -speed, 0.0D);
    }

    private boolean handleRiseFirework() {
        if (!fireworkTimer.hasDelayed(elytraFly.boostDelay.getValue())) return false;
        if (!useFirework()) return false;
        fireworkTimer.reset();
        return true;
    }

    private boolean hasUsableFirework() {
        FindItemResult result = elytraFly.swapMode.is(ElytraFly.SwapMode.Silent)
                ? InvUtils.findInHotbar(Items.FIREWORK_ROCKET)
                : InvUtils.find(Items.FIREWORK_ROCKET);
        return result.found();
    }

    private boolean isVerticalClear(int blocks) {
        if (blocks <= 0 || mc.level == null) return true;
        BlockPos origin = mc.player.blockPosition();
        for (int offset = 1; offset <= blocks; offset++) {
            BlockPos pos = origin.below(offset);
            if (mc.level.getBlockState(pos).isCollisionShapeFullBlock(mc.level, pos)) return false;
        }
        return true;
    }

    private Vec3 upMove() {
        Vec3 up = rotationVector(-90.0F, 0.0F);
        double currentVertical = getSpeed().y;
        // 保持源 upMove() 的当前垂直速度累加逻辑，再应用垂直加速倍率。
        double vertical = currentVertical + elytraFly.verticalMultiple.getValue()
                * (up.y * 0.1D + (up.y * 1.5D - currentVertical) * 0.5D);
        if (vertical > elytraFly.verticalMaxSpeed.getValue()) vertical = elytraFly.verticalMaxSpeed.getValue();

        return new Vec3(0.0D, vertical, 0.0D);
    }

    private Vec3 getSpeed() {
        return vec.subtract(oVec);
    }

    private boolean isBoosting() {
        if (mc.level == null || mc.player == null) return false;
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof FireworkRocketEntity rocket
                    && rocket.getOwner() != null
                    && rocket.getOwner().is(mc.player)) {
                return true;
            }
        }
        return false;
    }


    private double potionMultiplier(boolean jump) {
        if (!potionSpeed.getValue()) return 1.0D;
        double multiplier = 1.0D;
        MobEffectInstance speedEffect = mc.player.getEffect(MobEffects.SPEED);
        MobEffectInstance slownessEffect = mc.player.getEffect(MobEffects.SLOWNESS);
        if (!jump && speedEffect != null) {
            multiplier *= 1.0D + (speedEffect.getAmplifier() + 1) * speedPotionMultiplier.getValue();
        }
        if (potionSlowness.getValue() && slownessEffect != null) {
            multiplier = slownessPotionMultiplier.getValue() / (slownessEffect.getAmplifier() + 1.0D);
        }
        return multiplier;
    }

    private boolean isMoveBindPress() {
        return mc.options.keyUp.isDown()
                || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown()
                || mc.options.keyRight.isDown();
    }

    private void changeRiseYaw() {
        if (yawVelocityTick == mc.player.tickCount) return;
        riseYaw += yawVelocity.getValue().floatValue();
        if (riseYaw >= 360.0F || riseYaw <= -360.0F) riseYaw %= 360.0F;
        yawVelocityTick = mc.player.tickCount;
    }

    /** 复用源 ElytraFly 的 doNormalFly 物理，避免上升时固定垂直速度导致手感和源代码不一致。 */
    private Vec3 doNormalFly(double pitchDegrees, float yaw) {
        mc.player.startFallFlying();

        Vec3 current = mc.player.getDeltaMovement();
        Vec3 look = rotationVector((float) -pitchDegrees, yaw);
        float pitch = (float) Math.toRadians(-pitchDegrees);
        double horizontalLook = Math.sqrt(look.x * look.x + look.z * look.z);
        double horizontalSpeed = current.horizontalDistance();
        double lookLength = look.length();
        double lift = Math.cos(pitch);
        lift *= lift * Math.min(1.0D, lookLength / 0.4D);

        current = current.add(0.0D, 0.08D * (-1.0D + lift * 0.75D), 0.0D);
        if (current.y < 0.0D && horizontalLook > 0.0D) {
            double amount = current.y * -0.1D * lift;
            current = current.add(look.x * amount / horizontalLook, amount, look.z * amount / horizontalLook);
        }
        if (pitch < 0.0F && horizontalLook > 0.0D) {
            double amount = horizontalSpeed * -Math.sin(pitch) * 0.04D;
            current = current.add(-look.x * amount / horizontalLook, amount * 3.2D, -look.z * amount / horizontalLook);
        }
        if (horizontalLook > 0.0D) {
            current = current.add(
                    (look.x / horizontalLook * horizontalSpeed - current.x) * 0.1D,
                    0.0D,
                    (look.z / horizontalLook * horizontalSpeed - current.z) * 0.1D
            );
        }
        return current.multiply(0.99D, 0.98D, 0.99D);
    }

    private Vec3 rotationVector(float pitch, float yaw) {
        float pitchRadians = pitch * ((float) Math.PI / 180.0F);
        float yawRadians = -yaw * ((float) Math.PI / 180.0F);
        float cosYaw = Mth.cos(yawRadians);
        float sinYaw = Mth.sin(yawRadians);
        float cosPitch = Mth.cos(pitchRadians);
        float sinPitch = Mth.sin(pitchRadians);
        return new Vec3(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }

    private void refreshWaterYawOffset() {
        if (!mc.player.isInLiquid()) {
            waterYawOffset = 0.0F;
            waterYawTick = mc.player.tickCount;
            return;
        }
        if (waterYawTick == mc.player.tickCount) return;
        waterYawTick = mc.player.tickCount;
        waterYawOffset += waterYawSpeedOffset.getValue().floatValue() * waterYawDirection;
        if (Math.abs(waterYawOffset) >= 20.0F) {
            waterYawOffset = Math.copySign(20.0F, waterYawOffset);
            waterYawDirection *= -1;
        }
    }

    private void updateDefensiveGravity() {
        if (mc.player.onGround() || mc.player.isPassenger()
                || mc.options.keyJump.isDown() || mc.options.keyShift.isDown()) return;

        var gravity = mc.player.getAttribute(Attributes.GRAVITY);
        if (gravity == null) return;

        if (ElytraSwap.INSTANCE.isEmergencyActive()) {
            gravity.setBaseValue(0.0D);
        } else if (gravity.getBaseValue() == 0.0D) {
            restoreGravity();
        }
    }

    private void restoreGravity() {
        var gravity = mc.player == null ? null : mc.player.getAttribute(Attributes.GRAVITY);
        if (gravity != null) gravity.setBaseValue(originalGravity == null ? 0.08D : originalGravity);
    }
}
