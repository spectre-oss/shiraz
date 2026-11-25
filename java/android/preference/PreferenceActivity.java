// SPDX-License-Identifier: GPL-3.0-only
package android.preference;

import android.app.ListActivity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;
import android.widget.ListView;

/** Legacy preference screen activity supporting programmatic and XML preference hierarchies. */
public abstract class PreferenceActivity extends ListActivity {
    public static final long HEADER_ID_UNDEFINED = -1L;

    public static final class Header implements Parcelable {
        public static final Parcelable.Creator<Header> CREATOR =
                new Parcelable.Creator<Header>() {
                    @Override
                    public Header createFromParcel(Parcel source) {
                        Header h = new Header();
                        h.readFromParcel(source);
                        return h;
                    }

                    @Override
                    public Header[] newArray(int size) {
                        return new Header[size];
                    }
                };

        public long id = HEADER_ID_UNDEFINED;
        public int titleRes;
        public CharSequence title;
        public int summaryRes;
        public CharSequence summary;
        public int breadCrumbTitleRes;
        public CharSequence breadCrumbTitle;
        public int breadCrumbShortTitleRes;
        public CharSequence breadCrumbShortTitle;
        public int iconRes;
        public String fragment;
        public Bundle fragmentArguments;
        public Intent intent;
        public Bundle extras;

        public Header() {
        }

        public CharSequence getTitle(Resources res) {
            if (titleRes != 0 && res != null) {
                return res.getText(titleRes);
            }
            return title;
        }

        public CharSequence getSummary(Resources res) {
            if (summaryRes != 0 && res != null) {
                return res.getText(summaryRes);
            }
            return summary;
        }

        public CharSequence getBreadCrumbTitle(Resources res) {
            if (breadCrumbTitleRes != 0 && res != null) {
                return res.getText(breadCrumbTitleRes);
            }
            return breadCrumbTitle;
        }

        public CharSequence getBreadCrumbShortTitle(Resources res) {
            if (breadCrumbShortTitleRes != 0 && res != null) {
                return res.getText(breadCrumbShortTitleRes);
            }
            return breadCrumbShortTitle;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(id);
            dest.writeInt(titleRes);
            dest.writeString(title == null ? null : title.toString());
            dest.writeInt(summaryRes);
            dest.writeString(summary == null ? null : summary.toString());
            dest.writeInt(breadCrumbTitleRes);
            dest.writeString(breadCrumbTitle == null ? null : breadCrumbTitle.toString());
            dest.writeInt(breadCrumbShortTitleRes);
            dest.writeString(breadCrumbShortTitle == null ? null : breadCrumbShortTitle.toString());
            dest.writeInt(iconRes);
            dest.writeString(fragment);
            dest.writeBundle(fragmentArguments);
            dest.writeParcelable(intent, flags);
            dest.writeBundle(extras);
        }

        public void readFromParcel(Parcel in) {
            id = in.readLong();
            titleRes = in.readInt();
            title = in.readString();
            summaryRes = in.readInt();
            summary = in.readString();
            breadCrumbTitleRes = in.readInt();
            breadCrumbTitle = in.readString();
            breadCrumbShortTitleRes = in.readInt();
            breadCrumbShortTitle = in.readString();
            iconRes = in.readInt();
            fragment = in.readString();
            fragmentArguments = in.readBundle(Header.class.getClassLoader());
            intent = in.readParcelable(Header.class.getClassLoader());
            extras = in.readBundle(Header.class.getClassLoader());
        }
    }

    private PreferenceManager manager;
    private PreferenceScreen screen;

    public PreferenceManager getPreferenceManager() {
        if (manager == null) {
            manager = new PreferenceManager(this, this);
        }
        return manager;
    }

    public PreferenceScreen getPreferenceScreen() {
        return screen;
    }

    public void setPreferenceScreen(PreferenceScreen value) {
        getPreferenceManager().screen(value);
        screen = value;
        setListAdapter(value == null ? null : value.getRootAdapter());
    }

    public Preference findPreference(CharSequence key) {
        return getPreferenceManager().findPreference(key);
    }

    public void addPreferencesFromResource(int resource) {
        setPreferenceScreen(getPreferenceManager().inflateFromResource(this, resource, getPreferenceScreen()));
    }

    public void addPreferencesFromIntent(Intent intent) {
        throw new UnsupportedOperationException("Preference intent discovery is not implemented");
    }

    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        return false;
    }

    @Override
    protected void onListItemClick(ListView list, View view, int position, long id) {
        if (screen != null && position >= 0 && position < screen.getPreferenceCount()) {
            screen.getPreference(position).performClick(screen);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (manager != null) {
            manager.dispatchActivityStop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (manager != null) {
            manager.dispatchActivityDestroy();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (manager != null) {
            manager.dispatchActivityResult(requestCode, resultCode, data);
        }
    }

    protected boolean isValidFragment(String name) {
        return false;
    }

    public boolean isMultiPane() {
        return false;
    }

    public boolean hasHeaders() {
        return false;
    }

    public boolean onIsMultiPane() {
        return false;
    }

    public boolean onIsHidingHeaders() {
        return true;
    }
}
