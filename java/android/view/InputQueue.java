// SPDX-License-Identifier: GPL-3.0-only
package android.view;

public final class InputQueue {
    InputQueue() {
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }

    public interface Callback {
        void onInputQueueCreated(InputQueue queue);
        void onInputQueueDestroyed(InputQueue queue);
    }
}
