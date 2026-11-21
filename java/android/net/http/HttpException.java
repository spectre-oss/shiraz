// SPDX-License-Identifier: GPL-3.0-only
package android.net.http;

import java.io.IOException;

public class HttpException extends IOException {
    public HttpException(String message, Throwable cause) {
        super(message, cause);
    }
}
