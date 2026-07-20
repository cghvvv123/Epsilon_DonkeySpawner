package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.RegistryListSetting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.awt.*;
import java.util.List;

public class HoleHUD extends HudModule {

    public static final HoleHUD INSTANCE = new HoleHUD();

    private final RegistryListSetting<Block> safeBlocks = blockListSetting("Safe Blocks",
            List.of(Blocks.OBSIDIAN, Blocks.BEDROCK, Blocks.CRYING_OBSIDIAN, Blocks.NETHERITE_BLOCK));
    private final DoubleSetting scale = doubleSetting("Scale", 1.5, 0.5, 3.0, 0.1);
    private final BoolSetting rotateWithPlayer = boolSetting("Rotate With Player", true);
    private final BoolSetting background = boolSetting("Background", false);
    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(15, 15, 15, 130), background::getValue);

    private HoleHUD() {
        super("Hole HUD", 0f, 0f, 72f, 72f);
    }

    @Override
    public void render(DeltaTracker deltaTracker) {
        float size = 48f * scale.getValue().floatValue();
        setBounds(size, size);
        if (background.getValue()) renderScope().roundRect(this.x, this.y, size, size, 4f, backgroundColor.getValue());
    }

    @Override
    public void renderOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (nullCheck()) return;

        Direction front = rotateWithPlayer.getValue() ? Direction.fromYRot(mc.player.getYRot()) : Direction.NORTH;
        Direction left = front.getCounterClockWise();
        Direction right = front.getClockWise();
        Direction back = front.getOpposite();
        Direction[] directions = {front, left, right, back};
        int[][] cells = {{1, 0}, {0, 1}, {2, 1}, {1, 2}};
        float s = scale.getValue().floatValue();

        for (int i = 0; i < directions.length; i++) {
            Block block = mc.level.getBlockState(mc.player.blockPosition().relative(directions[i])).getBlock();
            if (!safeBlocks.contains(block)) continue;
            ItemStack stack = block.asItem().getDefaultInstance();
            graphics.pose().pushMatrix();
            graphics.pose().translate(this.x + cells[i][0] * 16f * s, this.y + cells[i][1] * 16f * s);
            graphics.pose().scale(s, s);
            graphics.item(stack, 0, 0);
            graphics.pose().popMatrix();
        }
    }
}
