// SPDX-License-Identifier: GPL-3.0-only
package android.service.controls.actions;

public final class CommandAction extends ControlAction {
    public CommandAction(String templateId, String challengeValue) {
        super(templateId, challengeValue);
    }

    public CommandAction(String templateId) {
        this(templateId, null);
    }

    @Override
    public int getActionType() {
        return TYPE_COMMAND;
    }
}
