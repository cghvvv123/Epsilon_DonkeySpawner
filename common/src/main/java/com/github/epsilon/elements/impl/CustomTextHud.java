package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.settings.impl.StringListSetting;
import com.github.epsilon.settings.impl.StringSetting;
import com.github.epsilon.utils.misc.EpsilonStarscript;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;
import org.meteordev.starscript.Script;
import org.meteordev.starscript.Section;
import org.meteordev.starscript.compiler.Compiler;
import org.meteordev.starscript.compiler.Parser;
import org.meteordev.starscript.utils.StarscriptError;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 基于 Meteor Client TextHud 移植，每条 StringListSetting 内容渲染为独立面板。
 */
public final class CustomTextHud extends HudModule {

    public static final CustomTextHud INSTANCE = new CustomTextHud();

    private enum Shown {
        Always,
        WhenTrue,
        WhenFalse
    }

    private enum HorizontalAlignment {
        Left,
        Center,
        Right
    }

    private final SettingGroup sgGeneral = settingGroup("General");
    private final SettingGroup sgShown = settingGroup("Shown");
    private final SettingGroup sgAppearance = settingGroup("Appearance");
    private final SettingGroup sgBackground = settingGroup("Background");

    private final StringListSetting texts = stringListSetting("Text", List.of(
            "{epsilon.name} {epsilon.version}",
            "FPS: {fps} | Ping: {ping}ms"
    )).group(sgGeneral);
    private final IntSetting updateDelay = intSetting("Update Delay", 4, 0, 200, 1).group(sgGeneral);

    private final EnumSetting<Shown> shown = enumSetting("Shown", Shown.Always).group(sgShown);
    private final StringSetting condition = stringSetting("Condition", "true", () -> !shown.is(Shown.Always)).group(sgShown);

    private final DoubleSetting scale = doubleSetting("Scale", 0.72, 0.4, 3.0, 0.02).group(sgAppearance);
    private final DoubleSetting border = doubleSetting("Border", 5.0, 0.0, 24.0, 0.5).group(sgAppearance);
    private final DoubleSetting panelGap = doubleSetting("Panel Gap", 3.0, 0.0, 20.0, 0.5).group(sgAppearance);
    private final EnumSetting<HorizontalAlignment> alignment = enumSetting("Alignment", HorizontalAlignment.Left).group(sgAppearance);
    private final ColorSetting textColor = colorSetting("Text Color", new Color(245, 247, 250, 255)).group(sgAppearance);
    private final ColorSetting accentColor = colorSetting("Accent Color", new Color(130, 180, 255, 255)).group(sgAppearance);
    private final ColorSetting warningColor = colorSetting("Warning Color", new Color(255, 205, 90, 255)).group(sgAppearance);
    private final ColorSetting errorColor = colorSetting("Error Color", new Color(255, 100, 100, 255)).group(sgAppearance);

    private final BoolSetting background = boolSetting("Background", true).group(sgBackground);
    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(15, 15, 15, 150), background::getValue).group(sgBackground);
    private final DoubleSetting cornerRadius = doubleSetting("Corner Radius", 4.0, 0.0, 14.0, 0.5, background::getValue).group(sgBackground);
    private final BoolSetting backgroundBlur = boolSetting("Background Blur", false, background::getValue).group(sgBackground);

    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);
    private final List<CompiledEntry> entries = new ArrayList<>();
    private List<String> compiledSources = List.of();
    private Script conditionScript;
    private String compiledCondition = "";
    private Shown compiledShown;
    private String conditionError;
    private boolean conditionVisible = true;
    private int updateTimer;
    private boolean refreshRequested = true;
    private List<PanelLayout> panelLayouts = List.of();

    private CustomTextHud() {
        super("Custom Text HUD", 0.0f, 0.0f, 120.0f, 20.0f);
    }

    @Override
    protected void onEnable() {
        refreshRequested = true;
        updateTimer = 0;
        panelLayouts = List.of();
    }

    @Override
    protected void resetCustomState() {
        entries.clear();
        compiledSources = List.of();
        conditionScript = null;
        compiledCondition = "";
        compiledShown = null;
        conditionError = null;
        conditionVisible = true;
        refreshRequested = true;
        updateTimer = 0;
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Post event) {
        if (refreshRequested || updateTimer <= 0) {
            refreshScripts();
            updateTimer = updateDelay.getValue();
        } else {
            updateTimer--;
        }
    }

    @Override
    public void render(DeltaTracker deltaTracker) {
        syncCompiledSources();
        if (refreshRequested || entries.stream().anyMatch(entry -> entry.section == null && entry.error == null)) {
            refreshScripts();
        }

        boolean editor = mc.screen instanceof HudEditorScreen;
        if (!conditionVisible && !editor) {
            panelLayouts = List.of();
            setBounds(20.0f, 20.0f);
            return;
        }

        TextRenderer renderer = textRendererSupplier.get();
        float textScale = scale.getValue().floatValue();
        float padding = border.getValue().floatValue();
        float gap = panelGap.getValue().floatValue();
        float textHeight = renderer.getHeight(textScale);
        List<RenderEntry> renderEntries = buildRenderEntries(editor);

        if (renderEntries.isEmpty()) {
            panelLayouts = List.of();
            setBounds(80.0f, Math.max(18.0f, textHeight + padding * 2.0f));
            return;
        }

        float maxPanelWidth = 0.0f;
        for (RenderEntry entry : renderEntries) {
            maxPanelWidth = Math.max(maxPanelWidth, renderer.getWidth(entry.text, textScale) + padding * 2.0f);
        }
        float panelHeight = textHeight + padding * 2.0f;
        float totalHeight = renderEntries.size() * panelHeight + Math.max(0, renderEntries.size() - 1) * gap;
        setBounds(Math.max(20.0f, maxPanelWidth), Math.max(20.0f, totalHeight));

        float rowY = this.y;
        List<PanelLayout> layouts = new ArrayList<>(renderEntries.size());
        for (RenderEntry entry : renderEntries) {
            float textWidth = renderer.getWidth(entry.text, textScale);
            float panelWidth = textWidth + padding * 2.0f;
            float panelX = alignedPanelX(panelWidth, maxPanelWidth);
            layouts.add(new PanelLayout(panelX - this.x, rowY - this.y, panelWidth, panelHeight));
            drawPanel(panelX, rowY, panelWidth, panelHeight);
            drawSections(renderer, entry, panelX + padding, rowY + padding, textScale);
            rowY += panelHeight + gap;
        }
        panelLayouts = List.copyOf(layouts);
    }

    @Override
    public List<UiRect> getEditorBounds() {
        if (panelLayouts.isEmpty()) return super.getEditorBounds();
        return panelLayouts.stream()
                .map(layout -> new UiRect(x + layout.offsetX(), y + layout.offsetY(), layout.width(), layout.height()))
                .toList();
    }

    @Override
    protected boolean shouldRenderTextShadow() {
        return !background.getValue();
    }

    private void syncCompiledSources() {
        List<String> currentSources = List.copyOf(texts.getValue());
        if (!currentSources.equals(compiledSources)) {
            compiledSources = currentSources;
            compileEntries();
            refreshRequested = true;
        }

        String currentCondition = condition.getValue() == null ? "" : condition.getValue();
        if (!Objects.equals(currentCondition, compiledCondition) || compiledShown != shown.getValue()) {
            compiledCondition = currentCondition;
            compiledShown = shown.getValue();
            compileCondition();
            refreshRequested = true;
        }
    }

    private void compileEntries() {
        entries.clear();
        for (String source : compiledSources) {
            Parser.Result result = Parser.parse(source == null ? "" : source);
            if (result.hasErrors()) {
                entries.add(new CompiledEntry(source, null, null, result.errors.getFirst().toString()));
            } else {
                entries.add(new CompiledEntry(source, Compiler.compile(result), null, null));
            }
        }
    }

    private void compileCondition() {
        conditionScript = null;
        conditionError = null;
        if (shown.is(Shown.Always)) return;

        Parser.Result result = Parser.parse(compiledCondition);
        if (result.hasErrors()) {
            conditionError = result.errors.getFirst().toString();
        } else {
            conditionScript = Compiler.compile(result);
        }
    }

    private void refreshScripts() {
        syncCompiledSources();
        for (CompiledEntry entry : entries) {
            if (entry.script == null) continue;
            try {
                entry.section = EpsilonStarscript.ENGINE.run(entry.script);
                entry.error = null;
            } catch (StarscriptError error) {
                entry.section = null;
                entry.error = error.getMessage();
            }
        }

        conditionVisible = evaluateCondition();
        refreshRequested = false;
    }

    private boolean evaluateCondition() {
        if (shown.is(Shown.Always)) return true;
        if (conditionScript == null) return false;
        conditionError = null;
        try {
            Section result = EpsilonStarscript.ENGINE.run(conditionScript);
            boolean value = result != null && Boolean.parseBoolean(result.toString());
            return shown.is(Shown.WhenTrue) ? value : !value;
        } catch (StarscriptError error) {
            conditionError = error.getMessage();
            return false;
        }
    }

    private List<RenderEntry> buildRenderEntries(boolean editor) {
        List<RenderEntry> result = new ArrayList<>();
        if (conditionError != null && editor) {
            result.add(new RenderEntry(new Section(3, conditionError), conditionError, true));
        }
        if (!conditionVisible && !editor) return result;

        for (CompiledEntry entry : entries) {
            if (entry.error != null) {
                result.add(new RenderEntry(new Section(3, entry.error), entry.error, true));
                continue;
            }
            if (entry.section == null || entry.section.toString().isBlank()) {
                if (editor) {
                    String preview = entry.source == null || entry.source.isBlank() ? "Custom Text" : entry.source;
                    result.add(new RenderEntry(new Section(0, preview), preview, false));
                }
                continue;
            }
            result.add(new RenderEntry(entry.section, entry.section.toString(), false));
        }
        return result;
    }

    private float alignedPanelX(float panelWidth, float maxPanelWidth) {
        return this.x + switch (alignment.getValue()) {
            case Left -> 0.0f;
            case Center -> (maxPanelWidth - panelWidth) / 2.0f;
            case Right -> maxPanelWidth - panelWidth;
        };
    }

    private void drawPanel(float x, float y, float width, float height) {
        if (!background.getValue()) return;
        float radius = cornerRadius.getValue().floatValue();
        if (backgroundBlur.getValue()) BlurShader.INSTANCE.render(x, y, width, height, radius, 5);
        renderScope().roundRect(x, y, width, height, radius, backgroundColor.getValue());
    }

    private void drawSections(TextRenderer renderer, RenderEntry entry, float x, float y, float textScale) {
        float sectionX = x;
        Section section = entry.section;
        while (section != null) {
            renderScope().text(section.text, sectionX, y, textScale, entry.error ? errorColor.getValue() : sectionColor(section.index));
            sectionX += renderer.getWidth(section.text, textScale);
            section = section.next;
        }
    }

    private Color sectionColor(int index) {
        return switch (index) {
            case 1 -> accentColor.getValue();
            case 2 -> warningColor.getValue();
            case 3 -> errorColor.getValue();
            default -> textColor.getValue();
        };
    }

    private static final class CompiledEntry {
        private final String source;
        private final Script script;
        private Section section;
        private String error;

        private CompiledEntry(String source, Script script, Section section, String error) {
            this.source = source;
            this.script = script;
            this.section = section;
            this.error = error;
        }
    }

    private record RenderEntry(Section section, String text, boolean error) {
    }

    private record PanelLayout(float offsetX, float offsetY, float width, float height) {
    }
}
