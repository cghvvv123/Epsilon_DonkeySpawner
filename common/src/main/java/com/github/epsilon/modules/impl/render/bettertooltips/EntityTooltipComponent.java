package com.github.epsilon.modules.impl.render.bettertooltips;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.github.epsilon.Constants.mc;

public class EntityTooltipComponent implements ClientTooltipComponent, EpsilonTooltipData {
    private final LivingEntity entity;
    private static double spin;

    public EntityTooltipComponent(LivingEntity entity) {
        this.entity = entity;
    }

    @Override
    public ClientTooltipComponent getComponent() {
        return this;
    }

    @Override
    public int getHeight(Font font) {
        return 48;
    }

    @Override
    public int getWidth(Font font) {
        return 64;
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        LivingEntityRenderState state = (LivingEntityRenderState) mc.getEntityRenderDispatcher().getRenderer(entity).createRenderState(entity, 1);
        state.lightCoords = 15728880;
        state.shadowPieces.clear();
        state.outlineColor = 0;
        state.bodyRot = (float) (spin % 360);
        state.yRot = 0;
        state.xRot = 0;
        x += (width - getWidth(null)) / 2;
        y += 4;
        float scale = Math.max(getWidth(null), getHeight(null)) / 2f * 1.25f;
        graphics.entity(state, scale, new Vector3f(0, 0.1f, 0), new Quaternionf().rotateZ((float) Math.PI), null, x, y, x + getWidth(null), y + getHeight(null));
        spin += 3 * mc.getDeltaTracker().getGameTimeDeltaTicks();
    }
}
