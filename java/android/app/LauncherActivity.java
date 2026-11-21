// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Deprecated
public abstract class LauncherActivity extends ListActivity {
    private List<ListItem> mItems;
    private IconResizer mIconResizer;

    public static class ListItem {
        public ResolveInfo resolveInfo;
        public CharSequence label;
        public Drawable icon;
        public String packageName;
        public String className;
        public Bundle extras;

        public ListItem() {
        }
    }

    public class IconResizer {
        public IconResizer() {
        }

        public Drawable createIconThumbnail(Drawable icon) {
            return icon;
        }
    }

    public LauncherActivity() {
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mIconResizer = new IconResizer();
        mItems = makeListItems();
        onSetContentView();
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
    }

    @Override
    public void setTitle(int titleId) {
        super.setTitle(titleId);
    }

    protected void onSetContentView() {
        getListView();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Intent intent = intentForPosition(position);
        if (intent != null) {
            startActivity(intent);
        }
    }

    protected Intent intentForPosition(int position) {
        ListItem item = itemForPosition(position);
        if (item == null) {
            return null;
        }
        Intent intent = new Intent(getTargetIntent());
        if (item.packageName != null && item.className != null) {
            intent.setClassName(item.packageName, item.className);
        }
        if (item.extras != null) {
            intent.putExtras(item.extras);
        }
        return intent;
    }

    protected ListItem itemForPosition(int position) {
        if (mItems == null || position < 0 || position >= mItems.size()) {
            return null;
        }
        return mItems.get(position);
    }

    protected Intent getTargetIntent() {
        return new Intent();
    }

    protected List<ResolveInfo> onQueryPackageManager(Intent queryIntent) {
        PackageManager pm = getPackageManager();
        if (pm == null || queryIntent == null) {
            return Collections.emptyList();
        }
        List<ResolveInfo> list = pm.queryIntentActivities(queryIntent, 0);
        return list != null ? list : Collections.<ResolveInfo>emptyList();
    }

    public List<ListItem> makeListItems() {
        List<ResolveInfo> list = onQueryPackageManager(getTargetIntent());
        ArrayList<ListItem> result = new ArrayList<ListItem>(list.size());
        PackageManager pm = getPackageManager();
        if (mIconResizer == null) {
            mIconResizer = new IconResizer();
        }
        for (int i = 0; i < list.size(); i++) {
            ResolveInfo info = list.get(i);
            ListItem item = new ListItem();
            item.resolveInfo = info;
            if (pm != null && info != null) {
                item.label = info.loadLabel(pm);
                Drawable d = info.loadIcon(pm);
                item.icon = d != null ? mIconResizer.createIconThumbnail(d) : null;
            }
            if (info != null && info.activityInfo != null) {
                item.packageName = info.activityInfo.packageName;
                item.className = info.activityInfo.name;
                if (item.label == null) {
                    item.label = item.className;
                }
            }
            result.add(item);
        }
        return result;
    }
}
