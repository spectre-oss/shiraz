// SPDX-License-Identifier: GPL-3.0-only
package android.hardware;

public abstract class TriggerEventListener {
    public TriggerEventListener() {
    }

    public abstract void onTrigger(TriggerEvent event);
}
