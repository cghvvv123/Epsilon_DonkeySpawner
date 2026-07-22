package com.github.epsilon.elements;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.gui.hudeditor.HudLayoutHelper;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.render.UiRenderBatch;
import com.github.epsilon.modules.Module;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.List;

public abstract class HudModule extends Module {

    public enum HorizontalAnchor {
        Left,
        Center,
        Right
    }

    public enum VerticalAnchor {
        Top,
        Center,
        Bottom
    }

    public float x, y, width, height;
    private float anchorX, anchorY;

    private final float defaultX, defaultY;
    private final float defaultAnchorX, defaultAnchorY;

    private HorizontalAnchor horizontalAnchor = HorizontalAnchor.Left;
    private VerticalAnchor verticalAnchor = VerticalAnchor.Top;
    private UiTree.Scope currentRenderScope;

    public HudModule(String name, float width, float height) {
        this(name, 0f, 0f, width, height);
    }

    public HudModule(String name, float x, float y, float width, float height) {
        super(name, null);

        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.anchorX = x;
        this.anchorY = y;
        this.defaultX = x;
        this.defaultY = y;
        this.defaultAnchorX = x;
        this.defaultAnchorY = y;
    }

    @Override
    public void reset() {
        super.reset();
        resetLayout();
    }

    private void resetLayout() {
        horizontalAnchor = HorizontalAnchor.Left;
        verticalAnchor = VerticalAnchor.Top;
        anchorX = defaultAnchorX;
        anchorY = defaultAnchorY;
        applyRenderPosition(defaultX, defaultY, false);
    }

    public final void updateLayout() {
        applyRenderPosition(getAnchoredRenderX(), getAnchoredRenderY(), false);
    }

    protected final void setBounds(float width, float height) {
        boolean changed = this.width != width || this.height != height;
        float oldWidth = this.width;
        float oldHeight = this.height;
        HorizontalAnchor resizeAnchor = getResizeHorizontalAnchor();
        VerticalAnchor verticalResizeAnchor = getResizeVerticalAnchor();
        float resizedX = switch (resizeAnchor) {
            case Left -> this.x;
            case Center -> this.x + (oldWidth - width) / 2.0f;
            case Right -> this.x + oldWidth - width;
        };
        float resizedY = switch (verticalResizeAnchor) {
            case Top -> this.y;
            case Center -> this.y + (oldHeight - height) / 2.0f;
            case Bottom -> this.y + oldHeight - height;
        };
        this.width = width;
        this.height = height;
        if (changed) {
            applyRenderPosition(resizedX, resizedY, false);
            anchorX = HudLayoutHelper.toAnchorX(horizontalAnchor, this.x, this.width, getScreenWidth());
            anchorY = HudLayoutHelper.toAnchorY(verticalAnchor, this.y, this.height, getScreenHeight());
        }
    }

    protected HorizontalAnchor getResizeHorizontalAnchor() {
        return horizontalAnchor;
    }

    protected VerticalAnchor getResizeVerticalAnchor() {
        return verticalAnchor;
    }

    public final boolean contains(double mouseX, double mouseY) {
        return getEditorBounds().stream().anyMatch(bounds -> bounds.contains(mouseX, mouseY));
    }

    /**
     * 返回 HUD 编辑器中用于命中检测和绘制框线的区域。
     * 普通 HUD 只有一个区域；复合 HUD 可以返回多个独立区域。
     */
    public List<UiRect> getEditorBounds() {
        return List.of(new UiRect(x, y, width, height));
    }

    public int getEditorPartAt(double mouseX, double mouseY) {
        List<UiRect> bounds = getEditorBounds();
        for (int i = bounds.size() - 1; i >= 0; i--) {
            if (bounds.get(i).contains(mouseX, mouseY)) return i;
        }
        return -1;
    }

    public UiRect getEditorPartBounds(int part) {
        List<UiRect> bounds = getEditorBounds();
        if (part < 0 || part >= bounds.size()) return new UiRect(x, y, width, height);
        return bounds.get(part);
    }

    public void moveEditorPartTo(int part, float x, float y) {
        moveTo(x, y);
    }

    public final void moveTo(float x, float y) {
        applyRenderPosition(x, y, true);
    }

    public final void moveBy(float deltaX, float deltaY) {
        moveTo(x + deltaX, y + deltaY);
    }

    public final void loadLegacyPosition(float renderX, float renderY) {
        horizontalAnchor = HorizontalAnchor.Left;
        verticalAnchor = VerticalAnchor.Top;
        anchorX = renderX;
        anchorY = renderY;
        applyRenderPosition(renderX, renderY, false);
    }

    public final void setAnchorState(HorizontalAnchor horizontalAnchor, VerticalAnchor verticalAnchor, float anchorX, float anchorY) {
        this.horizontalAnchor = horizontalAnchor == null ? HorizontalAnchor.Left : horizontalAnchor;
        this.verticalAnchor = verticalAnchor == null ? VerticalAnchor.Top : verticalAnchor;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        applyRenderPosition(getAnchoredRenderX(), getAnchoredRenderY(), false);
    }

    public final HorizontalAnchor getHorizontalAnchor() {
        return horizontalAnchor;
    }

    public final VerticalAnchor getVerticalAnchor() {
        return verticalAnchor;
    }

    public final float getAnchorX() {
        return anchorX;
    }

    public final float getAnchorY() {
        return anchorY;
    }

    private void applyRenderPosition(float renderX, float renderY, boolean updateAnchors) {
        int screenWidth = getScreenWidth();
        int screenHeight = getScreenHeight();
        float clampedX = Mth.clamp(renderX, 0.0f, Math.max(0.0f, screenWidth - width));
        float clampedY = Mth.clamp(renderY, 0.0f, Math.max(0.0f, screenHeight - height));

        if (updateAnchors) {
            horizontalAnchor = HudLayoutHelper.resolveHorizontalAnchor(clampedX, width, screenWidth);
            verticalAnchor = HudLayoutHelper.resolveVerticalAnchor(clampedY, height, screenHeight);
        }

        this.x = clampedX;
        this.y = clampedY;
        if (updateAnchors) {
            this.anchorX = HudLayoutHelper.toAnchorX(horizontalAnchor, clampedX, width, screenWidth);
            this.anchorY = HudLayoutHelper.toAnchorY(verticalAnchor, clampedY, height, screenHeight);
        }
    }

    private float getAnchoredRenderX() {
        int screenWidth = getScreenWidth();
        return HudLayoutHelper.getRenderX(horizontalAnchor, anchorX, width, screenWidth);
    }

    private float getAnchoredRenderY() {
        return HudLayoutHelper.getRenderY(verticalAnchor, anchorY, height, getScreenHeight());
    }

    private int getScreenWidth() {
        if (mc.getWindow() == null) {
            return 0;
        }
        return LuminRenderSystem.getScaledWidthInt();
    }

    private int getScreenHeight() {
        if (mc.getWindow() == null) {
            return 0;
        }
        return LuminRenderSystem.getScaledHeightInt();
    }

    public final void renderWithBatch(DeltaTracker deltaTracker, UiRenderBatch renderBatch) {
        UiTree.Scope previous = currentRenderScope;
        UiTree.Scope scope = new UiTree.Scope();
        scope.setTextShadow(shouldRenderTextShadow());
        currentRenderScope = scope;
        render(deltaTracker);
        currentRenderScope = previous;
        renderBatch.render(UiTree.from(scope));
    }

    protected final UiTree.Scope renderScope() {
        if (currentRenderScope == null) {
            throw new IllegalStateException("HUD elements must render through renderWithBatch.");
        }
        return currentRenderScope;
    }

    /**
     * 没有背景时由 HUD 文本启用阴影，提高纯文字 HUD 在浅色场景中的可读性。
     */
    protected boolean shouldRenderTextShadow() {
        return false;
    }

    public abstract void render(DeltaTracker deltaTracker);

    public void renderOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
    }

}
