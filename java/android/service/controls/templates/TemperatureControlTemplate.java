// SPDX-License-Identifier: GPL-3.0-only
package android.service.controls.templates;

import java.util.Objects;

public final class TemperatureControlTemplate extends ControlTemplate {
    public static final int MODE_UNKNOWN = 0;
    public static final int MODE_OFF = 1;
    public static final int MODE_HEAT = 2;
    public static final int MODE_COOL = 3;
    public static final int MODE_HEAT_COOL = 4;
    public static final int MODE_ECO = 5;

    public static final int FLAG_MODE_OFF = 2;
    public static final int FLAG_MODE_HEAT = 4;
    public static final int FLAG_MODE_COOL = 8;
    public static final int FLAG_MODE_HEAT_COOL = 16;
    public static final int FLAG_MODE_ECO = 32;

    private final ControlTemplate mTemplate;
    private final int mCurrentMode;
    private final int mCurrentActiveMode;
    private final int mModes;

    public TemperatureControlTemplate(String templateId, ControlTemplate controlTemplate,
            int currentMode, int currentActiveMode, int modesFlag) {
        super(templateId);
        mTemplate = Objects.requireNonNull(controlTemplate, "controlTemplate must not be null");
        mCurrentMode = currentMode;
        mCurrentActiveMode = currentActiveMode;
        mModes = modesFlag;
    }

    public ControlTemplate getTemplate() { return mTemplate; }
    public int getCurrentMode() { return mCurrentMode; }
    public int getCurrentActiveMode() { return mCurrentActiveMode; }
    public int getModes() { return mModes; }

    @Override
    public int getTemplateType() {
        return TYPE_TEMPERATURE;
    }
}
