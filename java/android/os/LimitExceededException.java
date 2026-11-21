// SPDX-License-Identifier: GPL-3.0-only
package android.os;

public class LimitExceededException extends IllegalStateException {
    public LimitExceededException() {
        super();
    }

    public LimitExceededException(String message) {
        super(message);
    }
}
