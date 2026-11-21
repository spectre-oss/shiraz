// SPDX-License-Identifier: GPL-3.0-only
package android.service.controls.actions;

import java.util.Objects;

public abstract class ControlAction {
    public static final int TYPE_ERROR = -1;
    public static final int TYPE_BOOLEAN = 1;
    public static final int TYPE_FLOAT = 2;
    public static final int TYPE_MODE = 4;
    public static final int TYPE_COMMAND = 5;

    public static final int RESPONSE_UNKNOWN = 0;
    public static final int RESPONSE_OK = 1;
    public static final int RESPONSE_FAIL = 2;
    public static final int RESPONSE_CHALLENGE_ACK = 3;
    public static final int RESPONSE_CHALLENGE_PIN = 4;
    public static final int RESPONSE_CHALLENGE_PASSPHRASE = 5;

    private static final ControlAction ERROR_ACTION = new ControlAction("", null) {
        @Override
        public int getActionType() {
            return TYPE_ERROR;
        }
    };

    private final String mTemplateId;
    private final String mChallengeValue;

    ControlAction(String templateId, String challengeValue) {
        mTemplateId = Objects.requireNonNull(templateId, "templateId must not be null");
        mChallengeValue = challengeValue;
    }

    public static final boolean isValidResponse(int response) {
        return response >= RESPONSE_UNKNOWN && response <= RESPONSE_CHALLENGE_PASSPHRASE;
    }

    public abstract int getActionType();

    public String getTemplateId() {
        return mTemplateId;
    }

    public String getChallengeValue() {
        return mChallengeValue;
    }

    public static ControlAction getErrorAction() {
        return ERROR_ACTION;
    }
}
