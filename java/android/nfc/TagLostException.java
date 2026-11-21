// SPDX-License-Identifier: GPL-3.0-only
package android.nfc;

import java.io.IOException;

public class TagLostException extends IOException {
    public TagLostException() {
        super();
    }

    public TagLostException(String message) {
        super(message);
    }
}
