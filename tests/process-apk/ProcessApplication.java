// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.processfixture;

/** Exercise process identity while the app class itself is being initialized. */
public final class ProcessApplication extends android.app.Application {
    private static final Object thread;
    static {
        try {
            java.io.FileInputStream input = new java.io.FileInputStream("/proc/self/cmdline");
            java.io.ByteArrayOutputStream name = new java.io.ByteArrayOutputStream();
            try { int value; while ((value = input.read()) > 0) name.write(value); }
            finally { input.close(); }
            if (!"org.shiraz.processfixture".equals(name.toString("UTF-8")))
                throw new AssertionError("Native process name missing during app initialization");
            System.out.println("PROCESS_INITIAL_ARGV_IDENTITY_OK");
        } catch (java.io.IOException error) { throw new AssertionError(error); }
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
        if (android.os.Debug.isDebuggerConnected()) throw new AssertionError("Unexpected debugger in fixture process");
        if (query("currentActivityThread") != thread || query("currentApplication") != this)
            throw new AssertionError("Application binding replaced process identity");
        System.out.println("PROCESS_APPLICATION_IDENTITY_OK");
    }
}
