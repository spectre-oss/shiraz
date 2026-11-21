// SPDX-License-Identifier: GPL-3.0-only
package android.service.controls.actions;

public final class ModeAction extends ControlAction {
    private final int mNewMode;

    public ModeAction(String templateId, int newMode, String challengeValue) {
        super(templateId, challengeValue);
        mNewMode = newMode;
    }

    public ModeAction(String templateId, int newMode) {
        this(templateId, newMode, null);
    }

    @Override
    public int getActionType() {
        return TYPE_MODE;
    }

    public int getNewMode() {
        return mNewMode;
    }
}
