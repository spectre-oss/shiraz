// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;

public class JsPromptResult extends JsResult {
    private String mStringResult;

    public JsPromptResult() {
    }

    public void confirm(String result) {
        mStringResult = result;
        confirm();
    }

    public String getStringResult() {
        return mStringResult;
    }
}
