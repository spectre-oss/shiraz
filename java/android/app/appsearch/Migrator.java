// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

public abstract class Migrator {
    public Migrator() {
    }

    public abstract boolean shouldMigrate(int currentVersion, int finalVersion);

    public abstract GenericDocument onUpgrade(
            int currentVersion, int finalVersion, GenericDocument document);

    public abstract GenericDocument onDowngrade(
            int currentVersion, int finalVersion, GenericDocument document);
}
