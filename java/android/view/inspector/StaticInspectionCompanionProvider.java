// SPDX-License-Identifier: GPL-3.0-only

package android.view.inspector;

public class StaticInspectionCompanionProvider implements InspectionCompanionProvider {
    private static final String COMPANION_SUFFIX = "$InspectionCompanion";

    public StaticInspectionCompanionProvider() {
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> InspectionCompanion<T> provide(Class<T> cls) {
        if (cls == null) {
            return null;
        }
        String companionName = cls.getName() + COMPANION_SUFFIX;
        try {
            Class<?> companionClass = cls.getClassLoader().loadClass(companionName);
            if (InspectionCompanion.class.isAssignableFrom(companionClass)) {
                return (InspectionCompanion<T>) companionClass.getDeclaredConstructor().newInstance();
            }
        } catch (ClassNotFoundException e) {
            return null;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return null;
    }
}
