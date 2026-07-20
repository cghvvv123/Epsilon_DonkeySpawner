package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.awt.*;

public class MapHUD extends HudModule {

    public static final MapHUD INSTANCE = new MapHUD();

    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.5, 2.0, 0.1);
    private final BoolSetting background = boolSetting("Background", true);
    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(15, 15, 15, 150), background::getValue);

    private MapHUD() {
        super("Map HUD", 0f, 0f, 128f, 128f);
    }

    @Override
    public void render(DeltaTracker deltaTracker) {
        float size = 128f * scale.getValue().floatValue();
        setBounds(size, size);
        if (background.getValue()) renderScope().roundRect(this.x, this.y, size, size, 3f, backgroundColor.getValue());
    }

    @Override
    public void renderOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (mc.level == null || mc.player == null) return;
        ItemStack stack = mc.player.getMainHandItem();
        if (!stack.is(net.minecraft.world.item.Items.FILLED_MAP)) stack = mc.player.getOffhandItem();
        MapId mapId = stack.get(DataComponents.MAP_ID);
        MapItemSavedData mapData = MapItem.getSavedData(stack, mc.level);
        if (mapId == null || mapData == null) return;

        MapRenderState state = new MapRenderState();
        mc.getMapRenderer().extractRenderState(mapId, mapData, state);
        if (state.texture == null) return;

        float s = scale.getValue().floatValue();
        graphics.pose().pushMatrix();
        graphics.pose().translate(this.x, this.y);
        graphics.pose().scale(s, s);
        graphics.map(state);
        graphics.pose().popMatrix();
    }
}
