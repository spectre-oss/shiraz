// SPDX-License-Identifier: GPL-3.0-only
package android.inputmethodservice;
/**
 * Allows clients such as Compose to distinguish IME contexts from app/dialog
 * contexts. Shiraz uses host text input; Android IME services are not hosted.
 */
public class InputMethodService extends AbstractInputMethodService {
    public InputMethodService() { }
    @Override public android.os.IBinder onBind(android.content.Intent intent) {
        throw new UnsupportedOperationException("Android input method service binding is not implemented");
    }
    public android.app.Dialog getWindow() {
        throw new UnsupportedOperationException("Android input method service windows are not implemented");
    }
}
