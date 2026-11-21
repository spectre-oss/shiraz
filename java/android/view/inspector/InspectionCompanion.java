// SPDX-License-Identifier: GPL-3.0-only

package android.view.inspector;

public interface InspectionCompanion<T> {
    void mapProperties(PropertyMapper propertyMapper);

    void readProperties(T inspectable, PropertyReader propertyReader);

    class UninitializedPropertyMapException extends RuntimeException {
        public UninitializedPropertyMapException() {
            super("Attempted to read properties of an inspectable before their mappings were initialized.");
        }
    }
}
