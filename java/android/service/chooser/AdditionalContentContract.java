// SPDX-License-Identifier: GPL-3.0-only
package android.service.chooser;

public interface AdditionalContentContract {
    public interface Columns {
        String URI = "uri";
    }

    public interface CursorExtraKeys {
        String POSITION = "position";
    }

    public interface MethodNames {
        String ON_SELECTION_CHANGED = "onSelectionChanged";
    }
}
