// SPDX-License-Identifier: GPL-3.0-only
package android.os.strictmode;

import android.content.Intent;

public final class UnsafeIntentLaunchViolation extends Violation {
    private final transient Intent intent;

    public UnsafeIntentLaunchViolation(Intent intent) {
        super("Launch of unsafe intent: " + intent);
        this.intent = intent;
    }

    public UnsafeIntentLaunchViolation(Intent intent, String message) {
        super(message);
        this.intent = intent;
    }

    public Intent getIntent() {
        return intent;
    }
}
