// SPDX-License-Identifier: GPL-3.0-only
package android.view;

/** Class that contains information about an event that triggers a search. */
public class SearchEvent {
    private final InputDevice mInputDevice;

    public SearchEvent(InputDevice inputDevice) {
        mInputDevice = inputDevice;
    }

    public InputDevice getInputDevice() {
        return mInputDevice;
    }
}
