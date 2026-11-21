// SPDX-License-Identifier: GPL-3.0-only

package android.view.inspector;

public interface InspectionCompanionProvider {
    <T> InspectionCompanion<T> provide(Class<T> cls);
}
