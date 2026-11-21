// SPDX-License-Identifier: GPL-3.0-only
package android.service.controls.templates;

import java.util.Objects;

public final class ToggleTemplate extends ControlTemplate {
    private final ControlButton mButton;

    public ToggleTemplate(String templateId, ControlButton button) {
        super(templateId);
        mButton = Objects.requireNonNull(button, "button must not be null");
    }

    public boolean isChecked() { return mButton.isChecked(); }
    public CharSequence getContentDescription() { return mButton.getActionDescription(); }

    @Override
    public int getTemplateType() {
        return TYPE_TOGGLE;
    }
}
