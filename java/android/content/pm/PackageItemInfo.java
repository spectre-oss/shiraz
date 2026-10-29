// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;
import android.content.res.XmlResourceParser;
public class PackageItemInfo {
    public String name, packageName;
    public int labelRes, icon, logo, banner;
    public android.os.Bundle metaData;
    public CharSequence nonLocalizedLabel;
    protected ApplicationInfo getApplicationInfo() { return null; }
    public CharSequence loadLabel(PackageManager pm) {
        if (pm == null) throw new NullPointerException("package manager");
        if (nonLocalizedLabel != null) return nonLocalizedLabel;
        ApplicationInfo app = this instanceof ApplicationInfo ? (ApplicationInfo) this : getApplicationInfo();
        if (app != null && this == app) return pm.getApplicationLabel(app);
        if (name != null) return name;
        return packageName == null ? "" : packageName;
    }
    public XmlResourceParser loadXmlMetaData(PackageManager pm, String name) {
        if (metaData != null) {
            int resid = metaData.getInt(name);
            if (resid != 0) return pm.getXml(packageName, resid, getApplicationInfo());
        }
        return null;
    }
}
