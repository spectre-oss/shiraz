// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.Context;
import android.os.LocaleList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class LocaleManager {
    private final Context mContext;
    private final Map<String, LocaleList> mPackageLocales = new HashMap<String, LocaleList>();
    private LocaleConfig mOverrideConfig;

    LocaleManager(Context context) {
        mContext = context;
    }

    LocaleManager() {
        mContext = null;
    }

    private String currentPackage() {
        return mContext != null && mContext.getPackageName() != null ? mContext.getPackageName() : "";
    }

    public synchronized void setApplicationLocales(LocaleList locales) {
        Objects.requireNonNull(locales, "locales");
        mPackageLocales.put(currentPackage(), locales);
    }

    public synchronized LocaleList getApplicationLocales() {
        return getApplicationLocales(currentPackage());
    }

    public synchronized LocaleList getApplicationLocales(String appPackageName) {
        Objects.requireNonNull(appPackageName, "appPackageName");
        LocaleList list = mPackageLocales.get(appPackageName);
        return list != null ? list : LocaleList.getEmptyLocaleList();
    }

    public LocaleList getSystemLocales() {
        LocaleList def = LocaleList.getDefault();
        return def != null ? def : LocaleList.getEmptyLocaleList();
    }

    public synchronized void setOverrideLocaleConfig(LocaleConfig localeConfig) {
        mOverrideConfig = localeConfig;
    }

    public synchronized LocaleConfig getOverrideLocaleConfig() {
        return mOverrideConfig;
    }
}
