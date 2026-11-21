// SPDX-License-Identifier: GPL-3.0-only
package android.net;

/** Thrown when parsing a URL or mailto URI fails. */
public class ParseException extends RuntimeException {
    public String response;

    public ParseException(String response) {
        super(response);
        this.response = response;
    }

    public ParseException(String response, Throwable cause) {
        super(response, cause);
        this.response = response;
    }
}
