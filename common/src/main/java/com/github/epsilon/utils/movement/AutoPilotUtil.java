package com.github.epsilon.utils.movement;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.github.epsilon.Constants.mc;

public final class AutoPilotUtil {

    public static final float INACTIVE_YAW = -999.0F;

    private AutoPilotUtil() {
    }

    public static float calcAutoMoveYaw(String destinationX, String destinationZ, int cruiseHeight,
                                        boolean playerDodge) {
        float yaw = INACTIVE_YAW;
        if (mc.player.getY() > cruiseHeight && !isMoveBindPress() && !mc.options.keyJump.isDown()) {
            Double x = null;
            Double z = null;
            try {
                x = Double.valueOf(destinationX);
                z = Double.valueOf(destinationZ);
            } catch (NumberFormatException ignored) {
            }
            if (x == null || z == null) return INACTIVE_YAW;

            Vec3 destination = new Vec3(x, mc.player.getY(), z);
            if (Math.sqrt(mc.player.distanceToSqr(destination)) > 40.0D) {
                yaw = getLegitRotations(destination)[0];
            }
        }

        if (playerDodge && yaw == INACTIVE_YAW && !isMoveBindPress() && !mc.options.keyJump.isDown()) {
            List<AbstractClientPlayer> players = mc.level.players().stream()
                    .filter(player -> mc.player.distanceTo(player) <= 16.0F && !mc.player.equals(player))
                    .collect(Collectors.toCollection(ArrayList::new));
            players.sort(Comparator.comparingDouble(mc.player::distanceTo));
            if (!players.isEmpty()) {
                yaw = getLegitRotations(players.getFirst().position())[0] + 180.0F;
            }
        }
        return yaw;
    }

    public static float[] getLegitRotations(Vec3 target) {
        Vec3 eyesPos = mc.player.getEyePosition();
        double diffX = target.x - eyesPos.x;
        double diffY = target.y - eyesPos.y;
        double diffZ = target.z - eyesPos.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));
        return new float[]{
                mc.player.getYHeadRot() + Mth.wrapDegrees(yaw - mc.player.getYHeadRot()),
                mc.player.getXRot() + Mth.wrapDegrees(pitch - mc.player.getXRot())
        };
    }

    public static boolean isMoveBindPress() {
        return mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
    }

    public static String getClipboardText() {
        try {
            String text = org.lwjgl.glfw.GLFW.glfwGetClipboardString(mc.getWindow().handle());
            if (text != null) return text;
        } catch (Exception ignored) {
        }

        try {
            java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            java.awt.datatransfer.Transferable contents = clipboard.getContents(null);
            if (contents != null && contents.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                return (String) contents.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static double[] parseCoordinates(String text) {
        if (text == null || text.isEmpty()) return null;

        Pattern pattern = Pattern.compile("-?\\d+(?:\\.\\d+)?");
        Matcher matcher = pattern.matcher(text);
        List<Double> numbers = new ArrayList<>();
        while (matcher.find()) {
            try {
                numbers.add(Double.parseDouble(matcher.group()));
            } catch (NumberFormatException ignored) {
            }
        }
        if (numbers.size() < 2) return null;
        return new double[]{numbers.getFirst(), numbers.getLast()};
    }
}
