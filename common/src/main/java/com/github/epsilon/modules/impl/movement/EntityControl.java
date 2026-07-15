package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.elements.impl.notification.NotificationMode;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.EntityMoveEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.settings.impl.KeybindSetting;
import com.github.epsilon.settings.impl.RegistryListSetting;
import com.github.epsilon.utils.player.ChatUtils;
import com.github.epsilon.utils.player.PlayerUtils;
import com.github.epsilon.utils.world.EntityTypeCategories;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class EntityControl extends Module {

    public static final EntityControl INSTANCE = new EntityControl();

    private static final long DOUBLE_TAP_DELAY = 250L;

    private enum ControlMode {
        Tradition,
        HappyGhast
    }

    private enum ActivationMode {
        Immediate,
        DoubleTapSpace
    }

    private enum AlertDisplayMode {
        Chat,
        Notification,
        Both
    }

    private final SettingGroup sgControl = settingGroup("Control");
    private final SettingGroup sgSpeed = settingGroup("Speed");
    private final SettingGroup sgFlight = settingGroup("Flight");
    private final SettingGroup sgMisc = settingGroup("Misc");

    private final RegistryListSetting<EntityType<?>> entities = entityTypeListSetting(
            "Entities",
            EntityTypeCategories.rideable()
    ).group(sgControl);
    private final BoolSetting spoofSaddle = boolSetting("Spoof Saddle", false).group(sgControl);
    private final BoolSetting maxJump = boolSetting("Max Jump", true).group(sgControl);
    private final BoolSetting cancelServerPackets = boolSetting("Cancel Server Packets", false).group(sgControl);
    private final EnumSetting<ControlMode> controlMode = enumSetting("Control Mode", ControlMode.Tradition).group(sgControl);
    private final EnumSetting<ActivationMode> activationMode = enumSetting("Activation Mode", ActivationMode.Immediate).group(sgControl);
    private final BoolSetting activationMessage = boolSetting(
            "Activation Message",
            true,
            () -> activationMode.is(ActivationMode.DoubleTapSpace)
    ).group(sgControl);
    private final EnumSetting<AlertDisplayMode> alertDisplayMode = enumSetting(
            "Alert Display Mode",
            AlertDisplayMode.Chat,
            () -> activationMode.is(ActivationMode.DoubleTapSpace)
    ).group(sgControl);
    private final BoolSetting persistentUntilDismount = boolSetting(
            "Persistent Until Dismount",
            true,
            () -> activationMode.is(ActivationMode.DoubleTapSpace)
    ).group(sgControl);
    private final KeybindSetting descendKey = keybindSetting(
            "Descend Key",
            GLFW.GLFW_KEY_LEFT_CONTROL,
            () -> controlMode.is(ControlMode.Tradition)
    ).group(sgControl);

    private final BoolSetting speed = boolSetting("Speed", false).group(sgSpeed);
    private final DoubleSetting horizontalSpeed = doubleSetting(
            "Horizontal Speed",
            100.0,
            0.0,
            400.0,
            0.1,
            speed::getValue
    ).group(sgSpeed);
    private final BoolSetting onlyOnGround = boolSetting("Only On Ground", false, speed::getValue).group(sgSpeed);
    private final BoolSetting inWater = boolSetting("In Water", true, speed::getValue).group(sgSpeed);

    private final BoolSetting flight = boolSetting("Fly", false).group(sgFlight);
    private final DoubleSetting verticalSpeed = doubleSetting(
            "Vertical Speed",
            20.0,
            0.0,
            50.0,
            0.1,
            flight::getValue
    ).group(sgFlight);
    private final DoubleSetting fallSpeed = doubleSetting(
            "Fall Speed",
            0.0,
            0.0,
            50.0,
            0.1,
            flight::getValue
    ).group(sgFlight);
    private final BoolSetting antiKick = boolSetting("Anti Fly Kick", true, flight::getValue).group(sgFlight);
    private final IntSetting delay = intSetting(
            "Delay",
            40,
            1,
            80,
            1,
            () -> flight.getValue() && antiKick.getValue()
    ).group(sgFlight);

    private final BoolSetting scaleMount = boolSetting("Scale Mount", false).group(sgMisc);
    private final DoubleSetting mountScale = doubleSetting(
            "Mount Scale",
            0.5,
            0.0,
            1.0,
            0.05,
            scaleMount::getValue
    ).group(sgMisc);
    private final BoolSetting scaleMountWithoutActivation = boolSetting(
            "Always Scale Mount",
            false,
            () -> scaleMount.getValue() && activationMode.is(ActivationMode.DoubleTapSpace)
    ).group(sgMisc);

    private int delayLeft;
    private double lastPacketY = Double.MAX_VALUE;
    private boolean sentPacket;
    private long lastSpacePressTime;
    private boolean doubleTapActive;
    private boolean lastJumpPressed;
    private boolean wasRiding;
    private Entity lastVehicle;

    private EntityControl() {
        super("Entity Control", Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        delayLeft = delay.getValue();
        sentPacket = false;
        lastPacketY = Double.MAX_VALUE;
        doubleTapActive = false;
        lastSpacePressTime = 0L;
        lastJumpPressed = false;
        wasRiding = false;
        lastVehicle = null;
    }

    @Override
    protected void onDisable() {
        if (lastVehicle != null) {
            lastVehicle.fallDistance = 0.0F;
        }
        lastVehicle = null;
        doubleTapActive = false;
    }

    public boolean spoofSaddle() {
        return isEnabled() && spoofSaddle.getValue();
    }

    public boolean maxJump() {
        return isEnabled() && maxJump.getValue();
    }

    public boolean cancelJump() {
        if (mc.player == null) return false;
        Entity vehicle = mc.player.getVehicle();
        return vehicle != null && flight.getValue() && canControl(vehicle);
    }

    public boolean shouldScaleMount() {
        if (mc.player == null || !isEnabled() || !scaleMount.getValue()) return false;
        if (activationMode.is(ActivationMode.DoubleTapSpace)
                && !doubleTapActive
                && !scaleMountWithoutActivation.getValue()) {
            return false;
        }
        Entity vehicle = mc.player.getVehicle();
        return vehicle != null && entities.getValue().contains(vehicle.getType());
    }

    public float getMountScale() {
        return mountScale.getValue().floatValue();
    }

    public Entity getMountedEntity() {
        return mc.player == null ? null : mc.player.getVehicle();
    }

    public boolean handlesEntity(Entity entity) {
        return isEnabled()
                && mc.player != null
                && entity != null
                && entity.getControllingPassenger() == mc.player
                && entities.getValue().contains(entity.getType());
    }

    public boolean canControl(Entity entity) {
        return handlesEntity(entity) && isControlActive();
    }

    public double getHorizontalSpeed() {
        return horizontalSpeed.getValue();
    }

    public boolean isControlActive() {
        if (!isEnabled()) return false;
        return activationMode.is(ActivationMode.Immediate) || doubleTapActive;
    }

    @EventHandler
    private void onEntityMove(EntityMoveEvent event) {
        if (!canControl(event.entity)) return;

        Entity entity = event.entity;
        double velocityX = entity.getDeltaMovement().x;
        double velocityY = entity.getDeltaMovement().y;
        double velocityZ = entity.getDeltaMovement().z;

        if (speed.getValue()
                && (!onlyOnGround.getValue() || entity.onGround() || entity.isFlyingVehicle())
                && (inWater.getValue() || !entity.isInWater())) {
            Vec3 velocity = PlayerUtils.getHorizontalVelocity(horizontalSpeed.getValue());
            velocityX = velocity.x;
            velocityZ = velocity.z;
        }

        if (flight.getValue()) {
            velocityY = 0.0;
            if (controlMode.is(ControlMode.Tradition)) {
                if (mc.options.keyJump.isDown()) {
                    velocityY += verticalSpeed.getValue() / 20.0;
                }
                if (InputConstants.isKeyDown(mc.getWindow(), descendKey.getValue())) {
                    velocityY -= verticalSpeed.getValue() / 20.0;
                } else {
                    velocityY -= fallSpeed.getValue() / 20.0;
                }
            } else {
                Vec3 lookVector = mc.player.getLookAngle();
                Vec3 horizontalLook = new Vec3(lookVector.x, 0.0, lookVector.z).normalize();
                Vec3 left = horizontalLook.cross(new Vec3(0.0, 1.0, 0.0)).normalize();

                double moveForward = 0.0;
                double moveRight = 0.0;
                double moveUp = 0.0;
                if (mc.options.keyUp.isDown()) moveForward += 1.0;
                if (mc.options.keyDown.isDown()) moveForward -= 1.0;
                if (mc.options.keyRight.isDown()) moveRight += 1.0;
                if (mc.options.keyLeft.isDown()) moveRight -= 1.0;
                if (mc.options.keyJump.isDown()) moveUp += 1.0;

                if (moveForward != 0.0 || moveRight != 0.0 || moveUp != 0.0) {
                    Vec3 movement = lookVector.scale(moveForward)
                            .add(left.scale(moveRight))
                            .add(0.0, moveUp, 0.0)
                            .normalize();
                    velocityX = movement.x * horizontalSpeed.getValue() / 20.0;
                    velocityY = movement.y * verticalSpeed.getValue() / 20.0;
                    velocityZ = movement.z * horizontalSpeed.getValue() / 20.0;
                } else {
                    velocityX = 0.0;
                    velocityY = -fallSpeed.getValue() / 20.0;
                    velocityZ = 0.0;
                }
            }
        }

        event.movement = new Vec3(velocityX, velocityY, velocityZ);
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) return;

        if (sentPacket && mc.player.getVehicle() != null) {
            Entity vehicle = mc.player.getVehicle();
            mc.player.connection.send(new ServerboundMoveVehiclePacket(
                    new Vec3(vehicle.getX(), lastPacketY, vehicle.getZ()),
                    vehicle.getYRot(),
                    vehicle.getXRot(),
                    vehicle.onGround()
            ));
            sentPacket = false;
        }
        delayLeft--;

        if (activationMode.is(ActivationMode.DoubleTapSpace)
                && mc.player.getVehicle() != null
                && entities.getValue().contains(mc.player.getVehicle().getType())) {
            boolean jumpPressed = mc.options.keyJump.isDown();
            if (jumpPressed && !lastJumpPressed) {
                long now = System.currentTimeMillis();
                if (now - lastSpacePressTime <= DOUBLE_TAP_DELAY) {
                    doubleTapActive = !doubleTapActive;
                    if (activationMessage.getValue()) {
                        String message = doubleTapActive
                                ? EpsilonTranslations.EntityControl.ACTIVATED.getTranslatedName()
                                : EpsilonTranslations.EntityControl.DEACTIVATED.getTranslatedName();
                        sendActivationAlert(message, doubleTapActive);
                    }
                }
                lastSpacePressTime = now;
            }
            lastJumpPressed = jumpPressed;
        }

        boolean currentlyRiding = mc.player.getVehicle() != null;
        if (wasRiding && !currentlyRiding && mc.options.keyShift.isDown()
                && persistentUntilDismount.getValue() && doubleTapActive) {
            doubleTapActive = false;
            if (activationMessage.getValue()) {
                sendActivationAlert(
                        EpsilonTranslations.EntityControl.DEACTIVATED_DISMOUNT.getTranslatedName(),
                        false
                );
            }
        }
        wasRiding = currentlyRiding;

        if (!currentlyRiding && !persistentUntilDismount.getValue()) {
            doubleTapActive = false;
        }

        Entity vehicle = mc.player.getVehicle();
        if (vehicle != null) {
            lastVehicle = vehicle;
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (nullCheck()) return;
        if (!(event.getPacket() instanceof ServerboundMoveVehiclePacket packet) || !antiKick.getValue()) return;

        double currentY = packet.position().y;
        Entity vehicle = mc.player.getVehicle();
        if (delayLeft <= 0 && !sentPacket && shouldFlyDown(currentY)
                && vehicle != null && !vehicle.onGround() && !vehicle.isFlyingVehicle()) {
            Vec3 position = new Vec3(packet.position().x, lastPacketY - 0.03130D, packet.position().z);
            ServerboundMoveVehiclePacket replacement = new ServerboundMoveVehiclePacket(
                    position,
                    packet.yRot(),
                    packet.xRot(),
                    packet.onGround()
            );
            event.cancel();
            sentPacket = true;
            mc.player.connection.send(replacement);
            delayLeft = delay.getValue();
            return;
        }
        lastPacketY = currentY;
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundMoveVehiclePacket && cancelServerPackets.getValue()) {
            event.cancel();
        }
    }

    private boolean shouldFlyDown(double currentY) {
        return currentY >= lastPacketY || lastPacketY - currentY < 0.03130D;
    }

    private void sendActivationAlert(String message, boolean activation) {
        AlertDisplayMode displayMode = alertDisplayMode.getValue();
        ChatFormatting color = activation ? ChatFormatting.GREEN : ChatFormatting.RED;
        if (displayMode == AlertDisplayMode.Chat || displayMode == AlertDisplayMode.Both) {
            ChatUtils.addChatMessage(Component.literal(message).withStyle(color));
        }
        if (displayMode == AlertDisplayMode.Notification || displayMode == AlertDisplayMode.Both) {
            Managers.NOTIFICATION.notifyHud(
                    message,
                    "",
                    activation ? NotificationMode.Success : NotificationMode.Error,
                    message.hashCode()
            );
        }
    }
}
