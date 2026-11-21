// SPDX-License-Identifier: GPL-3.0-only
package android.app.slice;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Manager for pinning, binding, and granting permissions to {@link Slice} URIs. */
public class SliceManager {
    public static final String CATEGORY_SLICE = "android.app.slice.category.SLICE";
    public static final String SLICE_METADATA_KEY = "android.metadata.SLICE_URI";

    private final Context mContext;
    private final Map<Uri, Set<SliceSpec>> mPinnedSlices =
            new LinkedHashMap<Uri, Set<SliceSpec>>();
    private final Map<String, Set<Uri>> mGrantedPermissions =
            new LinkedHashMap<String, Set<Uri>>();

    public SliceManager() {
        this.mContext = null;
    }

    public SliceManager(Context context) {
        this.mContext = Objects.requireNonNull(context, "context");
    }

    public synchronized void pinSlice(Uri uri, Set<SliceSpec> specs) {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(specs, "specs");
        boolean firstPin = !mPinnedSlices.containsKey(uri);
        mPinnedSlices.put(uri, Collections.unmodifiableSet(new LinkedHashSet<SliceSpec>(specs)));
        if (firstPin && mContext != null && "content".equals(uri.getScheme())) {
            Bundle extras = new Bundle();
            extras.putString(SliceProvider.EXTRA_BIND_URI, uri.toString());
            try {
                mContext.getContentResolver().call(uri, SliceProvider.METHOD_PIN, null, extras);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public synchronized void unpinSlice(Uri uri) {
        Objects.requireNonNull(uri, "uri");
        if (mPinnedSlices.remove(uri) != null
                && mContext != null
                && "content".equals(uri.getScheme())) {
            Bundle extras = new Bundle();
            extras.putString(SliceProvider.EXTRA_BIND_URI, uri.toString());
            try {
                mContext.getContentResolver().call(uri, SliceProvider.METHOD_UNPIN, null, extras);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public synchronized Set<SliceSpec> getPinnedSpecs(Uri uri) {
        Objects.requireNonNull(uri, "uri");
        Set<SliceSpec> specs = mPinnedSlices.get(uri);
        return specs == null ? Collections.<SliceSpec>emptySet() : specs;
    }

    public synchronized List<Uri> getPinnedSlices() {
        return Collections.unmodifiableList(new ArrayList<Uri>(mPinnedSlices.keySet()));
    }

    public Collection<Uri> getSliceDescendants(Uri uri) {
        Objects.requireNonNull(uri, "uri");
        if (mContext == null || !"content".equals(uri.getScheme())) {
            return Collections.emptyList();
        }
        Bundle extras = new Bundle();
        extras.putString(SliceProvider.EXTRA_BIND_URI, uri.toString());
        try {
            Bundle res =
                    mContext.getContentResolver()
                            .call(uri, SliceProvider.METHOD_GET_DESCENDANTS, null, extras);
            if (res == null) {
                return Collections.emptyList();
            }
            ArrayList<String> raw = res.getStringArrayList(SliceProvider.EXTRA_SLICE_DESCENDANTS);
            if (raw == null) {
                return Collections.emptyList();
            }
            ArrayList<Uri> out = new ArrayList<Uri>(raw.size());
            for (String s : raw) {
                if (s != null) {
                    out.add(Uri.parse(s));
                }
            }
            return out;
        } catch (IllegalArgumentException missingProvider) {
            return Collections.emptyList();
        }
    }

    public Slice bindSlice(Uri uri, Set<SliceSpec> supportedSpecs) {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(supportedSpecs, "supportedSpecs");
        if (mContext == null || !"content".equals(uri.getScheme())) {
            return null;
        }
        Bundle extras = new Bundle();
        extras.putString(SliceProvider.EXTRA_BIND_URI, uri.toString());
        extras.putParcelableArrayList(
                SliceProvider.EXTRA_SUPPORTED_SPECS, new ArrayList<SliceSpec>(supportedSpecs));
        try {
            Bundle res =
                    mContext.getContentResolver()
                            .call(uri, SliceProvider.METHOD_SLICE, null, extras);
            return res == null ? null : (Slice) res.getParcelable(SliceProvider.EXTRA_SLICE);
        } catch (IllegalArgumentException missingProvider) {
            return null;
        }
    }

    public Uri mapIntentToUri(Intent intent) {
        Objects.requireNonNull(intent, "intent");
        Uri data = intent.getData();
        if (data != null && SLICE_TYPE.equals(intent.getType())) {
            return data;
        }
        if (mContext == null) {
            return data != null && "content".equals(data.getScheme()) ? data : null;
        }
        String authority = data != null ? data.getAuthority() : intent.getPackage();
        if (authority == null) {
            return null;
        }
        Uri target = new Uri.Builder().scheme("content").authority(authority).build();
        Bundle extras = new Bundle();
        extras.putParcelable(SliceProvider.EXTRA_INTENT, intent);
        try {
            Bundle res =
                    mContext.getContentResolver()
                            .call(target, SliceProvider.METHOD_MAP_ONLY_INTENT, null, extras);
            if (res == null) {
                return null;
            }
            String mapped = res.getString(SliceProvider.EXTRA_SLICE);
            return mapped == null ? null : Uri.parse(mapped);
        } catch (IllegalArgumentException missingProvider) {
            return null;
        }
    }

    private static final String SLICE_TYPE = SliceProvider.SLICE_TYPE;

    public Slice bindSlice(Intent intent, Set<SliceSpec> supportedSpecs) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(supportedSpecs, "supportedSpecs");
        if (intent.getComponent() == null
                && intent.getPackage() == null
                && intent.getData() == null) {
            throw new IllegalArgumentException(
                    "Slice intent must be explicit; specify package, component, or data URI");
        }
        Uri data = intent.getData();
        if (data != null && SLICE_TYPE.equals(intent.getType())) {
            return bindSlice(data, supportedSpecs);
        }
        Uri mapped = mapIntentToUri(intent);
        return mapped == null ? null : bindSlice(mapped, supportedSpecs);
    }

    public synchronized int checkSlicePermission(Uri uri, int pid, int uid) {
        Objects.requireNonNull(uri, "uri");
        if (uid == 0) {
            return PackageManager.PERMISSION_GRANTED;
        }
        if (mContext != null && pid == Process.myPid() && uid == Process.myUid()) {
            Set<Uri> own = mGrantedPermissions.get(mContext.getPackageName());
            if (own != null && hasUriOrAncestor(own, uri)) {
                return PackageManager.PERMISSION_GRANTED;
            }
        }
        if (pid == Process.myPid() && uid == Process.myUid()) {
            for (Set<Uri> granted : mGrantedPermissions.values()) {
                if (hasUriOrAncestor(granted, uri)) {
                    return PackageManager.PERMISSION_GRANTED;
                }
            }
        }
        return PackageManager.PERMISSION_DENIED;
    }

    public synchronized void grantSlicePermission(String toPackage, Uri uri) {
        Objects.requireNonNull(toPackage, "toPackage");
        Objects.requireNonNull(uri, "uri");
        Set<Uri> set = mGrantedPermissions.get(toPackage);
        if (set == null) {
            set = new LinkedHashSet<Uri>();
            mGrantedPermissions.put(toPackage, set);
        }
        set.add(uri);
    }

    public synchronized void revokeSlicePermission(String toPackage, Uri uri) {
        Objects.requireNonNull(toPackage, "toPackage");
        Objects.requireNonNull(uri, "uri");
        Set<Uri> set = mGrantedPermissions.get(toPackage);
        if (set != null) {
            String prefix = uri.toString();
            ArrayList<Uri> toRemove = new ArrayList<Uri>();
            for (Uri candidate : set) {
                if (candidate.equals(uri) || candidate.toString().startsWith(prefix + "/")) {
                    toRemove.add(candidate);
                }
            }
            set.removeAll(toRemove);
        }
    }

    private static boolean hasUriOrAncestor(Set<Uri> granted, Uri target) {
        if (granted.contains(target)) {
            return true;
        }
        String targetStr = target.toString();
        for (Uri g : granted) {
            String gStr = g.toString();
            if (targetStr.startsWith(gStr.endsWith("/") ? gStr : gStr + "/")) {
                return true;
            }
        }
        return false;
    }
}
