// SPDX-License-Identifier: GPL-3.0-only
package android.preference;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.net.Uri;
import android.text.TextUtils;
import android.util.AttributeSet;

/** {@link Preference} launching the ringtone picker intent and persisting the chosen {@link Uri}. */
public class RingtonePreference extends Preference
        implements PreferenceManager.OnActivityResultListener {
    private static final String ACTION_RINGTONE_PICKER = "android.intent.action.RINGTONE_PICKER";
    private static final String EXTRA_EXISTING_URI = "android.intent.extra.ringtone.EXISTING_URI";
    private static final String EXTRA_SHOW_DEFAULT = "android.intent.extra.ringtone.SHOW_DEFAULT";
    private static final String EXTRA_DEFAULT_URI = "android.intent.extra.ringtone.DEFAULT_URI";
    private static final String EXTRA_SHOW_SILENT = "android.intent.extra.ringtone.SHOW_SILENT";
    private static final String EXTRA_TYPE = "android.intent.extra.ringtone.TYPE";
    private static final String EXTRA_TITLE = "android.intent.extra.ringtone.TITLE";
    private static final String EXTRA_PICKED_URI = "android.intent.extra.ringtone.PICKED_URI";

    private int ringtoneType = 1; // RingtoneManager.TYPE_RINGTONE
    private boolean showDefault = true;
    private boolean showSilent = true;
    private int requestCode = -1;

    public RingtonePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (attrs != null || defStyleAttr != 0 || defStyleRes != 0) {
            shiraz.runtime.XmlAttributes x =
                    new shiraz.runtime.XmlAttributes(context, attrs, defStyleAttr, defStyleRes);
            ringtoneType = x.integer("ringtoneType", 1);
            showDefault = x.bool("showDefault", true);
            showSilent = x.bool("showSilent", true);
        }
    }

    public RingtonePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public RingtonePreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0x01010093);
    }

    public RingtonePreference(Context context) {
        this(context, null);
    }

    public int getRingtoneType() {
        return ringtoneType;
    }

    public void setRingtoneType(int type) {
        ringtoneType = type;
    }

    public boolean getShowDefault() {
        return showDefault;
    }

    public void setShowDefault(boolean showDefault) {
        this.showDefault = showDefault;
    }

    public boolean getShowSilent() {
        return showSilent;
    }

    public void setShowSilent(boolean showSilent) {
        this.showSilent = showSilent;
    }

    @Override
    protected void onClick() {
        Intent intent = new Intent(ACTION_RINGTONE_PICKER);
        onPrepareRingtonePickerIntent(intent);
        PreferenceManager pm = getPreferenceManager();
        PreferenceFragment owningFragment = pm != null ? pm.getFragment() : null;
        if (owningFragment != null && owningFragment.getActivity() != null) {
            owningFragment.getActivity().startActivityForResult(intent, requestCode);
        } else {
            Activity activity = pm != null ? pm.getActivity() : null;
            if (activity != null) {
                activity.startActivityForResult(intent, requestCode);
            } else {
                getContext().startActivity(intent);
            }
        }
    }

    protected void onPrepareRingtonePickerIntent(Intent ringtonePickerIntent) {
        ringtonePickerIntent.putExtra(EXTRA_EXISTING_URI, onRestoreRingtone());
        ringtonePickerIntent.putExtra(EXTRA_SHOW_DEFAULT, showDefault);
        if (showDefault) {
            ringtonePickerIntent.putExtra(
                    EXTRA_DEFAULT_URI,
                    Uri.parse("content://settings/system/"
                            + (ringtoneType == 2
                                    ? "notification_sound"
                                    : (ringtoneType == 4 ? "alarm_alert" : "ringtone"))));
        }
        ringtonePickerIntent.putExtra(EXTRA_SHOW_SILENT, showSilent);
        ringtonePickerIntent.putExtra(EXTRA_TYPE, ringtoneType);
        if (getTitle() != null) {
            ringtonePickerIntent.putExtra(EXTRA_TITLE, getTitle().toString());
        }
    }

    protected void onSaveRingtone(Uri ringtoneUri) {
        persistString(ringtoneUri != null ? ringtoneUri.toString() : "");
    }

    protected Uri onRestoreRingtone() {
        final String uriString = getPersistedString(null);
        return !TextUtils.isEmpty(uriString) ? Uri.parse(uriString) : null;
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValueObj) {
        String defaultValue = (String) defaultValueObj;
        if (restorePersistedValue) {
            return;
        }
        if (!TextUtils.isEmpty(defaultValue)) {
            onSaveRingtone(Uri.parse(defaultValue));
        }
    }

    @Override
    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        super.onAttachedToHierarchy(preferenceManager);
        preferenceManager.registerOnActivityResultListener(this);
        requestCode = preferenceManager.allocateRequestCode();
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == this.requestCode) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                Uri uri = data.getParcelableExtra(EXTRA_PICKED_URI);
                if (callChangeListener(uri != null ? uri.toString() : "")) {
                    onSaveRingtone(uri);
                }
            }
            return true;
        }
        return false;
    }
}
