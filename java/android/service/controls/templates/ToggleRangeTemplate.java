// SPDX-License-Identifier: GPL-3.0-only
package android.service.controls.templates;

import java.util.Objects;

public final class ToggleRangeTemplate extends ControlTemplate {
    private final ControlButton mControlButton;
    private final RangeTemplate mRangeTemplate;

    public ToggleRangeTemplate(String templateId, ControlButton button, RangeTemplate range) {
        super(templateId);
        mControlButton = Objects.requireNonNull(button, "button must not be null");
        mRangeTemplate = Objects.requireNonNull(range, "range must not be null");
    }

    public ToggleRangeTemplate(String templateId, boolean checked, CharSequence actionDescription,
            RangeTemplate range) {
        this(templateId, new ControlButton(checked, actionDescription), range);
    }

    public RangeTemplate getRange() { return mRangeTemplate; }
    public boolean isChecked() { return mControlButton.isChecked(); }
    public CharSequence getActionDescription() { return mControlButton.getActionDescription(); }

    @Override
    public int getTemplateType() {
        return TYPE_TOGGLE_RANGE;
    }
}
