package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.gui.utils.RegistryListUi;
import com.github.epsilon.settings.impl.RegistryListSetting;

import static com.github.epsilon.Constants.mc;

public class RegistryListSettingWidget extends AbstractSetSettingWidget<RegistryListSetting<?>> {

    public RegistryListSettingWidget(RegistryListSetting<?> setting) {
        super(setting);
    }

    @Override
    protected int elementCount() {
        return setting.size();
    }

    @Override
    protected String labelText() {
        return RegistryListUi.labelText(setting.getRegistryType());
    }

    @Override
    protected void openPopup() {
        if (mc.screen instanceof HudEditorScreen) {
            HudEditorScreen.INSTANCE.openRegistryListSettingPopup(setting);
        } else {
            DropdownScreen.INSTANCE.openRegistryListSettingPopup(setting);
        }
    }

}
