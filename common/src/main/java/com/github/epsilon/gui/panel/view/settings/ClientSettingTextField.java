package com.github.epsilon.gui.panel.view.settings;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.control.TextFieldEditor;
import com.github.epsilon.gui.panel.utils.IMEFocusHelper;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import java.awt.*;

public class ClientSettingTextField {

    private static final long HOVER_DURATION = 120L;
    private final TextFieldEditor editor;
    private final Animation hoverAnimation = new Animation(Easing.EASE_OUT_CUBIC, HOVER_DURATION);
    private final Animation focusAnimation = new Animation(Easing.EASE_OUT_CUBIC, HOVER_DURATION);
    private boolean focused;
    private TextRenderer lastTextRenderer;
    private TextFieldEditor.VisibleSlice lastSlice;
    private float lastTextX;
    private float lastTextScale;

    public ClientSettingTextField(int maxLength) {
        editor = new TextFieldEditor(maxLength);
        hoverAnimation.setStartValue(0.0f);
        focusAnimation.setStartValue(0.0f);
    }

    public void buildUi(UiTree.Scope scope, UiRect bounds, int mouseX, int mouseY,
                        TextRenderer textRenderer, String placeholder, float textScale, String trailingHint) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        float hoverProgress = scope.animate(hoverAnimation, hovered);
        float focusProgress = scope.animate(focusAnimation, focused);
        float textInset = 10.0f;
        float textHeight = textRenderer.getHeight(textScale);
        float textX = bounds.x() + textInset;
        float textY = bounds.y() + (bounds.height() - textHeight) / 2.0f;

        boolean showPlaceholder = editor.getText().isEmpty() && !focused;
        TextFieldEditor.VisibleSlice slice = null;
        String display;
        float drawTextX = textX;
        if (showPlaceholder) {
            display = trimToWidth(placeholder, textRenderer, textScale, bounds.width() - textInset * 2.0f);
            lastSlice = null;
            lastTextRenderer = null;
        } else {
            slice = editor.visibleSlice(bounds.width() - textInset * 2.0f,
                    value -> textRenderer.getWidth(value, textScale));
            display = slice.text();
            drawTextX = textX;
            lastSlice = slice;
            lastTextRenderer = textRenderer;
            lastTextX = drawTextX;
            lastTextScale = textScale;
        }

        UiTree.SelectionRange selection = null;
        if (slice != null && focused && slice.hasSelection()) {
            selection = new UiTree.SelectionRange(slice.selectionStart(), slice.selectionEnd());
        }
        Color textColor = showPlaceholder ? MD3Theme.TEXT_MUTED : MD3Theme.TEXT_PRIMARY;
        scope.input(bounds, focused, hovered ? 0.6f : 0.0f,
                focusProgress, MD3Theme.PRIMARY, 1.0f,
                drawTextX - bounds.x(), display, textScale, textColor,
                selection, selection == null ? null : MD3Theme.withAlpha(MD3Theme.PRIMARY, 90),
                focused && slice != null ? slice.cursor() : null,
                focused ? MD3Theme.TEXT_PRIMARY : null,
                focused && trailingHint != null && !trailingHint.isBlank() && !editor.getText().isEmpty() ? trailingHint : null,
                0.56f,
                focused && trailingHint != null && !trailingHint.isBlank() && !editor.getText().isEmpty() ? MD3Theme.TEXT_MUTED : null);

        if (focused && slice != null) {
            int cursor = Math.clamp(slice.cursor(), 0, display.length());
            float caretX = drawTextX + textRenderer.getWidth(display.substring(0, cursor), textScale);
            IMEFocusHelper.updateCursorPos(caretX, textY);
        }
    }

    public boolean focusIfContains(UiRect bounds, double mouseX, double mouseY) {
        if (!bounds.contains(mouseX, mouseY)) return false;
        focused = true;
        if (lastSlice != null && lastTextRenderer != null) {
            editor.beginSelection(editor.resolveCursor(mouseX, lastTextX, lastSlice,
                    value -> lastTextRenderer.getWidth(value, lastTextScale)), false);
        } else {
            editor.moveCursorToEnd();
        }
        IMEFocusHelper.activate();
        return true;
    }

    public boolean mouseDragged(double mouseX) {
        if (!focused || !editor.isSelecting() || lastSlice == null || lastTextRenderer == null) return false;
        editor.dragSelection(editor.resolveCursor(mouseX, lastTextX, lastSlice,
                value -> lastTextRenderer.getWidth(value, lastTextScale)));
        return true;
    }

    public boolean mouseReleased() {
        if (!editor.isSelecting()) return false;
        editor.endSelection();
        return true;
    }

    public void blur() {
        if (focused) {
            focused = false;
            editor.endSelection();
            IMEFocusHelper.deactivate();
        }
    }

    public boolean keyPressed(KeyEvent event) {
        return focused && editor.keyPressed(event);
    }

    public boolean charTyped(CharacterEvent event) {
        return focused && editor.insert(event.codepointAsString());
    }

    public boolean hasActiveAnimations() {
        return !hoverAnimation.isFinished() || !focusAnimation.isFinished();
    }

    public boolean isFocused() {
        return focused;
    }

    public String getText() {
        return editor.getText();
    }

    public void setText(String text) {
        String value = text == null ? "" : text;
        if (editor.getText().equals(value)) return;
        editor.setText(value);
        lastSlice = null;
        lastTextRenderer = null;
    }

    public void clear() {
        editor.clear();
        lastSlice = null;
        lastTextRenderer = null;
    }

    public void setCursorToEnd() {
        editor.moveCursorToEnd();
    }

    private String trimToWidth(String value, TextRenderer textRenderer, float scale, float width) {
        if (value == null || value.isEmpty() || textRenderer.getWidth(value, scale) <= width) return value == null ? "" : value;
        String ellipsis = "...";
        if (textRenderer.getWidth(ellipsis, scale) >= width) return ellipsis;
        int low = 0;
        int high = value.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (textRenderer.getWidth(value.substring(0, mid) + ellipsis, scale) <= width) low = mid;
            else high = mid - 1;
        }
        return value.substring(0, low) + ellipsis;
    }
}
