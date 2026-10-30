// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.processfixture;

/** Exercise process identity while the app class itself is being initialized. */
public final class ProcessApplication extends android.app.Application {
    private static final Object thread;
    static {
        thread = query("currentActivityThread");
        if (thread == null) throw new AssertionError("ActivityThread missing during app initialization");
        if (query("currentApplication") != null) throw new AssertionError("Application published before construction");
        if (!"org.shiraz.processfixture".equals(query("currentPackageName")))
            throw new AssertionError("Package identity missing during app initialization");
        try {
            android.content.Context context = (android.content.Context) thread.getClass().getMethod("getSystemContext").invoke(thread);
            if (!"android".equals(context.getPackageName()) || context.getApplicationContext() != null)
                throw new AssertionError("System context identity");
            if (context.getResources() != android.content.res.Resources.getSystem())
                throw new AssertionError("System resources identity");
            if (!"org.shiraz.processfixture".equals(context.getPackageManager().getApplicationInfo("org.shiraz.processfixture", 0).packageName))
                throw new AssertionError("Early package metadata");
        } catch (Exception error) { throw new AssertionError(error); }
    }
    private static Object query(String method) {
        try { return Class.forName("android.app.ActivityThread").getMethod(method).invoke(null); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    @Override public void onCreate() {
        super.onCreate();
        if (query("currentActivityThread") != thread || query("currentApplication") != this)
            throw new AssertionError("Application binding replaced process identity");
        System.out.println("PROCESS_APPLICATION_IDENTITY_OK");
    }
}
