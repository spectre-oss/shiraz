// SPDX-License-Identifier: GPL-3.0-only
package android.net.http;

import java.util.concurrent.RejectedExecutionException;

public final class InlineExecutionProhibitedException extends RejectedExecutionException {
    public InlineExecutionProhibitedException() {
        super("Inline execution is prohibited for this request");
    }
}
