package com.github.epsilon.modules.impl.render.bettertooltips;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import static com.github.epsilon.Constants.mc;

public class MapTooltipComponent implements ClientTooltipComponent, EpsilonTooltipData {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/map/map_background.png");
    private final MapId mapId;
    private final double scale;
    private final MapRenderState state = new MapRenderState();

    public MapTooltipComponent(int mapId, double scale) {
        this.mapId = new MapId(mapId);
        this.scale = scale;
    }

    @Override
    public int getHeight(Font font) {
        return (int) ((128 + 16) * scale) + 2;
    }

    @Override
    public int getWidth(Font font) {
        return (int) ((128 + 16) * scale);
    }

    @Override
    public ClientTooltipComponent getComponent() {
        return this;
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        int size = (int) ((128 + 16) * scale);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0, 0, size, size, size, size);
        if (mc.level == null) return;
        MapItemSavedData map = MapItem.getSavedData(mapId, mc.level);
        if (map == null) return;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale((float) scale, (float) scale);
        graphics.pose().translate(8, 8);
        mc.getMapRenderer().extractRenderState(mapId, map, state);
        graphics.map(state);
        graphics.pose().popMatrix();
    }
}
