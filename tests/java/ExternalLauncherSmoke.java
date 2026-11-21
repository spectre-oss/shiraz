// SPDX-License-Identifier: GPL-3.0-only
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import java.util.ArrayList;
import java.util.List;
import shiraz.runtime.ExternalLauncher;

public final class ExternalLauncherSmoke {
    private static final class FixturePackages extends android.content.pm.PackageManager {
        @Override public java.util.List<android.content.pm.ResolveInfo> queryIntentActivities(Intent intent, int flags) {
            check(intent.getSelector() != null && intent.getSelector().getComponent() != null);
            android.content.pm.ActivityInfo activity = new android.content.pm.ActivityInfo();
            activity.packageName = "fixture.app"; activity.name = "fixture.app.Screen";
            activity.applicationInfo = new android.content.pm.ApplicationInfo();
            activity.applicationInfo.enabled = true;
            android.content.pm.ResolveInfo result = new android.content.pm.ResolveInfo();
            result.activityInfo = activity;
            return java.util.Collections.singletonList(result);
        }
        @Override public android.content.pm.ActivityInfo getActivityInfo(ComponentName name, int flags) throws NameNotFoundException { throw new NameNotFoundException(); }
        @Override public android.content.pm.ServiceInfo getServiceInfo(ComponentName name, int flags) throws NameNotFoundException { throw new NameNotFoundException(); }
        @Override public android.content.pm.PackageInfo getPackageInfo(String name, int flags) throws NameNotFoundException { throw new NameNotFoundException(); }
        @Override public android.content.pm.ApplicationInfo getApplicationInfo(String name, int flags) throws NameNotFoundException { throw new NameNotFoundException(); }
        @Override public CharSequence getApplicationLabel(android.content.pm.ApplicationInfo info) { return "fixture"; }
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    private static Intent browser(String uri) {
        return Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
                .setData(Uri.parse(uri));
    }
    public static void main(String[] args) {
        final List<String> calls = new ArrayList<String>();
        ExternalLauncher.setRunner(new ExternalLauncher.Runner() {
            public void run(List<String> command) { calls.addAll(command); }
        });
        check(ExternalLauncher.open(browser("https://example.invalid/help")));
        check(calls.size() == 2 && calls.get(0).equals(ExternalLauncher.opener())
                && calls.get(1).equals("https://example.invalid/help"));
        check(ExternalLauncher.externalTarget(browser("http://example.invalid/")) != null);
        check(ExternalLauncher.externalTarget(browser("mailto:someone@example.invalid")) == null);
        check(ExternalLauncher.externalTarget(browser("file:///tmp/test")) == null);
        check(ExternalLauncher.externalTarget(browser("https://example.invalid/a b")) == null);
        check(ExternalLauncher.externalTarget(Intent.makeMainSelectorActivity(
                Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)) == null);
        Intent ownComponent = browser("https://example.invalid/");
        ownComponent.setComponent(new ComponentName("some.browser", "Browser"));
        check(!ExternalLauncher.open(ownComponent) && calls.size() == 2);
        Intent scoped = browser("https://example.invalid/");
        scoped.getSelector().setPackage("some.browser");
        check(ExternalLauncher.externalTarget(scoped) == null);
        Intent explicit = browser("https://example.invalid/");
        explicit.getSelector().setComponent(new ComponentName("some.browser", "Browser"));
        check(ExternalLauncher.externalTarget(explicit) == null);
        Intent extraCategory = browser("https://example.invalid/");
        extraCategory.getSelector().addCategory(Intent.CATEGORY_DEFAULT);
        check(ExternalLauncher.externalTarget(extraCategory) == null);
        Intent nested = browser("https://example.invalid/");
        nested.getSelector().setSelector(new Intent(Intent.ACTION_VIEW));
        check(ExternalLauncher.externalTarget(nested) == null);
        Intent typed = browser("https://example.invalid/");
        typed.getSelector().setType("text/html");
        check(ExternalLauncher.externalTarget(typed) == null);
        Intent wrong = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_DEFAULT)
                .setData(Uri.parse("https://example.invalid/"));
        check(ExternalLauncher.externalTarget(wrong) == null);
        check(ExternalLauncher.externalTarget(new Intent(Intent.ACTION_VIEW,
                Uri.parse("mailto:someone@example.invalid"))) != null);
        Intent viewWithSelector = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.invalid/private"));
        viewWithSelector.setSelector(new Intent("sample.INTERNAL").setComponent(new ComponentName("fixture.app", "fixture.app.Screen")));
        check(!ExternalLauncher.open(viewWithSelector) && calls.size() == 2);
        shiraz.runtime.ActivityLaunchQueue queue = new shiraz.runtime.ActivityLaunchQueue(
                new shiraz.runtime.ActivityLaunchResolver("fixture.app", new FixturePackages()));
        queue.enqueue(viewWithSelector);
        shiraz.runtime.ActivityLaunchResolver.Request request = queue.poll();
        check(calls.size() == 2 && request != null);
        check(request.intent().getComponent().equals(new ComponentName("fixture.app", "fixture.app.Screen"))
                && "https://example.invalid/private".equals(request.intent().getDataString()));
        queue.close();
        Intent viewWithPackageSelector = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.invalid/private"));
        viewWithPackageSelector.setSelector(new Intent(Intent.ACTION_VIEW).setPackage("fixture.app"));
        check(!ExternalLauncher.open(viewWithPackageSelector) && calls.size() == 2);
        System.out.println("EXTERNAL_LAUNCHER_SMOKE_PASS");
    }
}
