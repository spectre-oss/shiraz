// SPDX-License-Identifier: GPL-3.0-only
package android.preference;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

/** Platform {@link Fragment} hosting a {@link PreferenceScreen} hierarchy. */
public abstract class PreferenceFragment extends Fragment {
    private static final String PREFERENCES_TAG = "android:preferences";

    public interface OnPreferenceStartFragmentCallback {
        boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref);
    }

    private PreferenceManager preferenceManager;
    private ListView list;
    private boolean havePrefs;
    private boolean initDone;

    public PreferenceFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context ctx = getActivity();
        if (ctx != null) {
            preferenceManager = new PreferenceManager(ctx, this);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Context ctx = getActivity() != null ? getActivity() : inflater.getContext();
        ListView listView = new ListView(ctx);
        listView.setId(0x0102000a);
        list = listView;
        return listView;
    }

    public void onViewCreated(View view, Bundle savedInstanceState) {
        if (view != null) {
            View rawList = view.findViewById(0x0102000a);
            if (rawList instanceof ListView) {
                list = (ListView) rawList;
            }
        }
        if (havePrefs) {
            bindPreferences();
        }
        initDone = true;
        if (savedInstanceState != null) {
            Bundle container = savedInstanceState.getBundle(PREFERENCES_TAG);
            if (container != null) {
                final PreferenceScreen preferenceScreen = getPreferenceScreen();
                if (preferenceScreen != null) {
                    preferenceScreen.restoreHierarchyState(container);
                }
            }
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (havePrefs) {
            bindPreferences();
        }
        initDone = true;
        if (savedInstanceState != null) {
            Bundle container = savedInstanceState.getBundle(PREFERENCES_TAG);
            if (container != null) {
                final PreferenceScreen preferenceScreen = getPreferenceScreen();
                if (preferenceScreen != null) {
                    preferenceScreen.restoreHierarchyState(container);
                }
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (preferenceManager != null) {
            preferenceManager.dispatchActivityStop();
        }
    }

    @Override
    public void onDestroyView() {
        list = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (preferenceManager != null) {
            preferenceManager.dispatchActivityDestroy();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (preferenceScreen != null) {
            Bundle container = new Bundle();
            preferenceScreen.saveHierarchyState(container);
            outState.putBundle(PREFERENCES_TAG, container);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (preferenceManager != null) {
            preferenceManager.dispatchActivityResult(requestCode, resultCode, data);
        }
    }

    public PreferenceManager getPreferenceManager() {
        if (preferenceManager == null && getActivity() != null) {
            preferenceManager = new PreferenceManager(getActivity(), this);
        }
        return preferenceManager;
    }

    public void setPreferenceScreen(PreferenceScreen preferenceScreen) {
        PreferenceManager pm = getPreferenceManager();
        if (pm != null && preferenceScreen != pm.getPreferenceScreen()) {
            pm.screen(preferenceScreen);
            if (preferenceScreen != null) {
                havePrefs = true;
                if (initDone) {
                    bindPreferences();
                }
            }
        }
    }

    public PreferenceScreen getPreferenceScreen() {
        PreferenceManager pm = getPreferenceManager();
        return pm == null ? null : pm.getPreferenceScreen();
    }

    public void addPreferencesFromIntent(Intent intent) {
        throw new UnsupportedOperationException("Preference intent discovery is not implemented");
    }

    public void addPreferencesFromResource(int preferencesResId) {
        PreferenceManager pm = getPreferenceManager();
        if (pm == null) {
            throw new IllegalStateException("This should be called after super.onCreate.");
        }
        setPreferenceScreen(pm.inflateFromResource(getActivity(), preferencesResId, getPreferenceScreen()));
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference.getFragment() != null
                && getActivity() instanceof OnPreferenceStartFragmentCallback) {
            return ((OnPreferenceStartFragmentCallback) getActivity())
                    .onPreferenceStartFragment(this, preference);
        }
        return false;
    }

    public Preference findPreference(CharSequence key) {
        PreferenceManager pm = getPreferenceManager();
        return pm == null ? null : pm.findPreference(key);
    }

    private void bindPreferences() {
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (preferenceScreen != null && list != null) {
            preferenceScreen.bind(list);
        }
    }
}
