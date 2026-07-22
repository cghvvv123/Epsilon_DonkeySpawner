package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.lib.UiTextMetrics;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.control.TextFieldEditor;
import com.github.epsilon.gui.panel.utils.IMEFocusHelper;
import com.github.epsilon.gui.theme.MD3Theme;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import java.util.function.Predicate;

public class DropdownTextField {

    private final TextFieldEditor editor;
    private boolean focused;
    private UiTextMetrics lastTextMetrics;
    private TextFieldEditor.VisibleSlice lastSlice;
    private float lastTextX;
    private float lastTextScale;

    public DropdownTextField() {
        this(TextFieldEditor.UNLIMITED_LENGTH);
    }

    public DropdownTextField(int maxLength) {
        this(maxLength, value -> true);
    }

    public DropdownTextField(int maxLength, Predicate<String> inputFilter) {
        this.editor = new TextFieldEditor(maxLength, inputFilter);
    }

    public void draw(UiTree.Scope scope, UiTextMetrics textMetrics, float x, float y, float width, float height, int mouseX, int mouseY, String placeholder, float textScale) {
        scope.roundRect(x, y, width, height, DropdownTheme.INPUT_RADIUS, DropdownTheme.inputSurface(focused));
        scope.outline(x, y, width, height, DropdownTheme.INPUT_RADIUS, 0.7f, focused ? MD3Theme.PRIMARY : MD3Theme.withAlpha(MD3Theme.OUTLINE, 90));

        boolean showPlaceholder = editor.getText().isEmpty() && !focused;
        float textY = y + (height - textMetrics.textHeight(textScale)) / 2.0f;
        float textX = x + 4.0f;
        String display;
        if (showPlaceholder) {
            display = trimToWidth(placeholder, textScale, width - 8.0f, textMetrics);
            clearLastLayout();
        } else {
            lastSlice = editor.visibleSlice(width - 8.0f, value -> textMetrics.textWidth(value, textScale));
            lastTextMetrics = textMetrics;
            lastTextX = textX;
            lastTextScale = textScale;
            display = lastSlice.text();
            drawSelection(scope, textMetrics, textX, textY, textScale, lastSlice);
        }
        scope.text(display, textX, textY, textScale, showPlaceholder ? MD3Theme.TEXT_MUTED : MD3Theme.TEXT_PRIMARY);

        if (focused) {
            float caretX = textX + textMetrics.textWidth(display.substring(0, Math.clamp(lastSlice.cursor(), 0, display.length())), textScale);
            drawCaret(scope, textMetrics, caretX, textY, textScale);
            IMEFocusHelper.updateCursorPos(caretX, textY);
        }
    }

    public void drawCentered(UiTree.Scope scope, UiTextMetrics textMetrics, float x, float y, float width, float height, int mouseX, int mouseY, String placeholder, float textScale) {
        scope.roundRect(x, y, width, height, DropdownTheme.INPUT_RADIUS, DropdownTheme.inputSurface(focused));
        scope.outline(x, y, width, height, DropdownTheme.INPUT_RADIUS, 0.7f, focused ? MD3Theme.PRIMARY : MD3Theme.withAlpha(MD3Theme.OUTLINE, 90));

        boolean showPlaceholder = editor.getText().isEmpty() && !focused;

        float textY = y + (height - textMetrics.textHeight(textScale)) / 2.0f;
        String visibleText;
        float textX;
        if (showPlaceholder) {
            visibleText = trimToWidth(placeholder, textScale, width - 8.0f, textMetrics);
            textX = x + (width - textMetrics.textWidth(visibleText, textScale)) * 0.5f;
            clearLastLayout();
        } else {
            lastSlice = editor.visibleSlice(width - 8.0f, value -> textMetrics.textWidth(value, textScale));
            visibleText = lastSlice.text();
            boolean clipped = lastSlice.start() > 0 || lastSlice.end() < editor.getText().length();
            textX = clipped ? x + 4.0f : x + (width - textMetrics.textWidth(visibleText, textScale)) * 0.5f;
            lastTextMetrics = textMetrics;
            lastTextX = textX;
            lastTextScale = textScale;
            drawSelection(scope, textMetrics, textX, textY, textScale, lastSlice);
        }
        scope.text(visibleText, textX, textY, textScale, showPlaceholder ? MD3Theme.TEXT_MUTED : MD3Theme.TEXT_PRIMARY);

        if (focused) {
            float caretX = textX + textMetrics.textWidth(visibleText.substring(0, Math.clamp(lastSlice.cursor(), 0, visibleText.length())), textScale);
            drawCaret(scope, textMetrics, caretX, textY, textScale);
            IMEFocusHelper.updateCursorPos(caretX, textY);
        }
    }

    public boolean focusIfContains(double mouseX, double mouseY, float x, float y, float width, float height) {
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            return false;
        }
        focused = true;
        editor.beginSelection(resolveCursor(mouseX), false);
        IMEFocusHelper.activate();
        return true;
    }

    public boolean focusIfContainsCentered(double mouseX, double mouseY, float x, float y, float width, float height) {
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            return false;
        }
        focused = true;
        editor.beginSelection(resolveCursor(mouseX), false);
        IMEFocusHelper.activate();
        return true;
    }

    public void focus() {
        focused = true;
        editor.moveCursorToEnd();
        IMEFocusHelper.activate();
    }

    public void blur() {
        if (focused) {
            focused = false;
            editor.endSelection();
            IMEFocusHelper.deactivate();
        }
    }

    public boolean mouseDragged(double mouseX) {
        if (!focused || !editor.isSelecting()) return false;
        editor.dragSelection(resolveCursor(mouseX));
        return true;
    }

    public boolean mouseReleased() {
        if (!editor.isSelecting()) return false;
        editor.endSelection();
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        return focused && editor.keyPressed(event);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return focused && editor.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(CharacterEvent event) {
        if (!focused) return false;
        return editor.insert(event.codepointAsString());
    }

    public boolean charTyped(String typedText) {
        if (!focused) return false;
        return editor.insert(typedText);
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
        clearLastLayout();
    }

    public void clear() {
        editor.clear();
        clearLastLayout();
    }

    public void setCursorToEnd() {
        editor.moveCursorToEnd();
    }

    private int resolveCursor(double mouseX) {
        if (lastSlice == null || lastTextMetrics == null) return editor.getText().length();
        return editor.resolveCursor(mouseX, lastTextX, lastSlice,
                value -> lastTextMetrics.textWidth(value, lastTextScale));
    }

    private void drawSelection(UiTree.Scope scope, UiTextMetrics textMetrics, float textX, float textY,
                               float textScale, TextFieldEditor.VisibleSlice slice) {
        if (!focused || !slice.hasSelection()) return;
        float start = textMetrics.textWidth(slice.text().substring(0, slice.selectionStart()), textScale);
        float end = textMetrics.textWidth(slice.text().substring(0, slice.selectionEnd()), textScale);
        scope.rect(textX + start, textY, Math.max(0.8f, end - start), textMetrics.textHeight(textScale),
                MD3Theme.withAlpha(MD3Theme.PRIMARY, 82));
    }

    private void clearLastLayout() {
        lastTextMetrics = null;
        lastSlice = null;
    }

    private void drawCaret(UiTree.Scope scope, UiTextMetrics textMetrics, float x, float y, float textScale) {
        if (System.currentTimeMillis() % 1000 > 500) {
            scope.rect(x, y, 0.8f, textMetrics.textHeight(textScale), MD3Theme.TEXT_PRIMARY);
        }
    }

    private String trimToWidth(String value, float scale, float maxWidth, UiTextMetrics textMetrics) {
        if (value == null || value.isEmpty()) return "";
        if (textMetrics.textWidth(value, scale) <= maxWidth) return value;
        String ellipsis = "...";
        float ellipsisWidth = textMetrics.textWidth(ellipsis, scale);
        if (ellipsisWidth >= maxWidth) return ellipsis;
        int low = 0;
        int high = value.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            String candidate = value.substring(0, mid) + ellipsis;
            if (textMetrics.textWidth(candidate, scale) <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return value.substring(0, low) + ellipsis;
    }

}
