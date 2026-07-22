package com.github.epsilon.gui.lib.control;

import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.function.Predicate;

import static com.github.epsilon.Constants.mc;

public final class TextFieldEditor {

    public static final int UNLIMITED_LENGTH = Integer.MAX_VALUE;

    private final int maxLength;
    private final Predicate<String> inputFilter;
    private String text = "";
    private int cursor;
    private int selectionAnchor;
    private int displayStart;
    private boolean selecting;

    public TextFieldEditor() {
        this(UNLIMITED_LENGTH);
    }

    public TextFieldEditor(int maxLength) {
        this(maxLength, value -> true);
    }

    public TextFieldEditor(int maxLength, Predicate<String> inputFilter) {
        this.maxLength = Math.max(0, maxLength);
        this.inputFilter = inputFilter == null ? value -> true : inputFilter;
    }

    public boolean keyPressed(KeyEvent event) {
        if (event.isSelectAll()) {
            cursor = text.length();
            selectionAnchor = 0;
            ensureCursorBounds();
            return true;
        }
        if (event.isCopy()) {
            mc.keyboardHandler.setClipboard(getSelectedText());
            return true;
        }
        if (event.isPaste()) {
            insert(mc.keyboardHandler.getClipboard());
            return true;
        }
        if (event.isCut()) {
            mc.keyboardHandler.setClipboard(getSelectedText());
            replaceSelection("");
            return true;
        }

        return switch (event.key()) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                delete(-1, event.hasControlDownWithQuirk());
                yield true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                delete(1, event.hasControlDownWithQuirk());
                yield true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                int target = hasSelection() && !event.hasShiftDown() && !event.hasControlDownWithQuirk()
                        ? getSelectionStart()
                        : event.hasControlDownWithQuirk() ? wordPosition(-1) : codePointPosition(-1);
                moveCursor(target, event.hasShiftDown());
                yield true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                int target = hasSelection() && !event.hasShiftDown() && !event.hasControlDownWithQuirk()
                        ? getSelectionEnd()
                        : event.hasControlDownWithQuirk() ? wordPosition(1) : codePointPosition(1);
                moveCursor(target, event.hasShiftDown());
                yield true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                moveCursor(0, event.hasShiftDown());
                yield true;
            }
            case GLFW.GLFW_KEY_END -> {
                moveCursor(text.length(), event.hasShiftDown());
                yield true;
            }
            default -> false;
        };
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
    }

    public boolean insert(String inserted) {
        if (inserted == null || inserted.isEmpty()) return false;

        StringBuilder accepted = new StringBuilder(inserted.length());
        inserted.codePoints().forEach(codePoint -> {
            if (codePoint >= 32 && codePoint != 127) {
                String candidate = new String(Character.toChars(codePoint));
                if (inputFilter.test(candidate)) accepted.append(candidate);
            }
        });
        if (accepted.isEmpty()) return false;

        int available = maxLength - (text.length() - selectionLength());
        if (available <= 0) return false;
        String value = truncateWithoutSplittingSurrogate(accepted.toString(), available);
        if (value.isEmpty()) return false;
        replaceSelection(value);
        return true;
    }

    public void beginSelection(int position, boolean extendSelection) {
        int safePosition = Math.clamp(position, 0, text.length());
        if (extendSelection) {
            if (!hasSelection()) selectionAnchor = cursor;
            cursor = safePosition;
        } else {
            cursor = safePosition;
            selectionAnchor = cursor;
        }
        selecting = true;
        ensureCursorBounds();
    }

    public void dragSelection(int position) {
        if (!selecting) return;
        cursor = Math.clamp(position, 0, text.length());
        ensureCursorBounds();
    }

    public void endSelection() {
        selecting = false;
    }

    public VisibleSlice visibleSlice(float maxWidth, TextWidth textWidth) {
        int length = text.length();
        cursor = Math.clamp(cursor, 0, length);
        displayStart = Math.clamp(displayStart, 0, length);
        float width = Math.max(0.0f, maxWidth);

        if (textWidth.width(text) <= width) {
            displayStart = 0;
            return createSlice(0, length);
        }

        if (cursor < displayStart) displayStart = cursor;
        while (displayStart < cursor && textWidth.width(text.substring(displayStart, cursor)) > width) {
            displayStart = Util.offsetByCodepoints(text, displayStart, 1);
        }

        int end = displayStart;
        while (end < length) {
            int next = Util.offsetByCodepoints(text, end, 1);
            if (textWidth.width(text.substring(displayStart, next)) > width) break;
            end = next;
        }

        while (cursor > end && displayStart < length) {
            displayStart = Util.offsetByCodepoints(text, displayStart, 1);
            end = displayStart;
            while (end < length) {
                int next = Util.offsetByCodepoints(text, end, 1);
                if (textWidth.width(text.substring(displayStart, next)) > width) break;
                end = next;
            }
        }
        return createSlice(displayStart, end);
    }

    public int resolveCursor(double mouseX, float textX, VisibleSlice slice, TextWidth textWidth) {
        if (mouseX <= textX) {
            if (selecting && slice.start() > 0) return Util.offsetByCodepoints(text, slice.start(), -1);
            return slice.start();
        }
        float visibleWidth = textWidth.width(slice.text());
        if (selecting && mouseX >= textX + visibleWidth && slice.end() < text.length()) {
            return Util.offsetByCodepoints(text, slice.end(), 1);
        }
        float previousWidth = 0.0f;
        int index = slice.start();
        while (index < slice.end()) {
            int next = Util.offsetByCodepoints(text, index, 1);
            float nextWidth = textWidth.width(text.substring(slice.start(), next));
            if (mouseX < textX + (previousWidth + nextWidth) * 0.5f) return index;
            previousWidth = nextWidth;
            index = next;
        }
        return slice.end();
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        String value = text == null ? "" : text;
        this.text = truncateWithoutSplittingSurrogate(value, maxLength);
        cursor = Math.min(cursor, this.text.length());
        selectionAnchor = cursor;
        displayStart = Math.min(displayStart, cursor);
    }

    public void clear() {
        text = "";
        cursor = 0;
        selectionAnchor = 0;
        displayStart = 0;
        selecting = false;
    }

    public void moveCursorToEnd() {
        cursor = text.length();
        selectionAnchor = cursor;
        ensureCursorBounds();
    }

    public int getCursor() {
        return cursor;
    }

    public boolean hasSelection() {
        return cursor != selectionAnchor;
    }

    public int getSelectionStart() {
        return Math.min(cursor, selectionAnchor);
    }

    public int getSelectionEnd() {
        return Math.max(cursor, selectionAnchor);
    }

    public boolean isSelecting() {
        return selecting;
    }

    private VisibleSlice createSlice(int start, int end) {
        int selectionStart = Math.clamp(getSelectionStart(), start, end) - start;
        int selectionEnd = Math.clamp(getSelectionEnd(), start, end) - start;
        return new VisibleSlice(text.substring(start, end), start, end, cursor - start, selectionStart, selectionEnd);
    }

    private void delete(int direction, boolean wholeWord) {
        if (hasSelection()) {
            replaceSelection("");
            return;
        }
        int target = wholeWord ? wordPosition(direction) : codePointPosition(direction);
        if (target == cursor) return;
        int start = Math.min(cursor, target);
        int end = Math.max(cursor, target);
        text = text.substring(0, start) + text.substring(end);
        cursor = start;
        selectionAnchor = cursor;
        ensureCursorBounds();
    }

    private void replaceSelection(String replacement) {
        int start = getSelectionStart();
        int end = getSelectionEnd();
        if (start == end && replacement.isEmpty()) return;
        text = text.substring(0, start) + replacement + text.substring(end);
        cursor = start + replacement.length();
        selectionAnchor = cursor;
        ensureCursorBounds();
    }

    private void moveCursor(int position, boolean extendSelection) {
        int oldCursor = cursor;
        boolean hadSelection = hasSelection();
        cursor = Math.clamp(position, 0, text.length());
        if (extendSelection) {
            if (!hadSelection) selectionAnchor = oldCursor;
        } else {
            selectionAnchor = cursor;
        }
        ensureCursorBounds();
    }

    private int codePointPosition(int direction) {
        return Util.offsetByCodepoints(text, cursor, direction);
    }

    private int wordPosition(int direction) {
        int position = cursor;
        if (direction < 0) {
            while (position > 0 && Character.isWhitespace(text.charAt(position - 1))) position--;
            while (position > 0 && !Character.isWhitespace(text.charAt(position - 1))) position--;
        } else {
            while (position < text.length() && !Character.isWhitespace(text.charAt(position))) position++;
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++;
        }
        return position;
    }

    private int selectionLength() {
        return getSelectionEnd() - getSelectionStart();
    }

    private String getSelectedText() {
        return hasSelection() ? text.substring(getSelectionStart(), getSelectionEnd()) : "";
    }

    private void ensureCursorBounds() {
        cursor = Math.clamp(cursor, 0, text.length());
        selectionAnchor = Math.clamp(selectionAnchor, 0, text.length());
        displayStart = Math.clamp(displayStart, 0, text.length());
    }

    private static String truncateWithoutSplittingSurrogate(String value, int maxLength) {
        if (value.length() <= maxLength) return value;
        int end = Math.max(0, maxLength);
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }

    @FunctionalInterface
    public interface TextWidth {
        float width(String text);
    }

    public record VisibleSlice(String text, int start, int end, int cursor, int selectionStart, int selectionEnd) {
        public boolean hasSelection() {
            return selectionStart != selectionEnd;
        }
    }
}
