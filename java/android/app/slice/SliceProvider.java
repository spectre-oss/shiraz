// SPDX-License-Identifier: GPL-3.0-only
package android.app.slice;

import android.app.PendingIntent;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Base {@link ContentProvider} for serving {@link Slice} instances in-process. */
public abstract class SliceProvider extends ContentProvider {
    public static final String SLICE_TYPE = "vnd.android.slice";

    static final String METHOD_SLICE = "bind_slice";
    static final String METHOD_MAP_INTENT = "map_slice";
    static final String METHOD_MAP_ONLY_INTENT = "map_only";
    static final String METHOD_PIN = "pin";
    static final String METHOD_UNPIN = "unpin";
    static final String METHOD_GET_DESCENDANTS = "get_descendants";

    static final String EXTRA_BIND_URI = "slice_uri";
    static final String EXTRA_SUPPORTED_SPECS = "supported_specs";
    static final String EXTRA_SLICE = "slice";
    static final String EXTRA_INTENT = "slice_intent";
    static final String EXTRA_SLICE_DESCENDANTS = "slice_descendants";

    private final String[] mAutoGrantPermissions;

    public SliceProvider(String... autoGrantPermissions) {
        Objects.requireNonNull(autoGrantPermissions, "autoGrantPermissions");
        this.mAutoGrantPermissions = autoGrantPermissions.clone();
    }

    public SliceProvider() {
        this.mAutoGrantPermissions = new String[0];
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
    }

    public Slice onBindSlice(Uri sliceUri, Set<SliceSpec> supportedSpecs) {
        return null;
    }

    public void onSlicePinned(Uri sliceUri) {
    }

    public void onSliceUnpinned(Uri sliceUri) {
    }

    public Collection<Uri> onGetSliceDescendants(Uri uri) {
        return Collections.emptyList();
    }

    public Uri onMapIntentToUri(Intent intent) {
        throw new UnsupportedOperationException(
                "This provider has not implemented intent to uri mapping");
    }

    public PendingIntent onCreatePermissionRequest(Uri sliceUri) {
        return null;
    }

    @Override
    public final int update(
            Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public final int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public final Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        return null;
    }

    @Override
    public final Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder,
            CancellationSignal cancellationSignal) {
        return null;
    }

    public final Cursor query(
            Uri uri,
            String[] projection,
            Bundle queryArgs,
            CancellationSignal cancellationSignal) {
        return null;
    }

    @Override
    public final Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public final String getType(Uri uri) {
        return SLICE_TYPE;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (method == null || extras == null) {
            return super.call(method, arg, extras);
        }
        if (METHOD_SLICE.equals(method)) {
            Uri uri = readUriExtra(extras, EXTRA_BIND_URI);
            Set<SliceSpec> specs = readSpecsExtra(extras);
            Slice slice = uri == null ? null : onBindSlice(uri, specs);
            Bundle b = new Bundle();
            b.putParcelable(EXTRA_SLICE, slice);
            return b;
        }
        if (METHOD_MAP_INTENT.equals(method)) {
            Intent intent = extras.getParcelable(EXTRA_INTENT);
            if (intent == null) {
                return null;
            }
            Uri uri = onMapIntentToUri(intent);
            Bundle b = new Bundle();
            if (uri != null) {
                Set<SliceSpec> specs = readSpecsExtra(extras);
                Slice slice = onBindSlice(uri, specs);
                b.putParcelable(EXTRA_SLICE, slice);
            } else {
                b.putParcelable(EXTRA_SLICE, null);
            }
            return b;
        }
        if (METHOD_MAP_ONLY_INTENT.equals(method)) {
            Intent intent = extras.getParcelable(EXTRA_INTENT);
            if (intent == null) {
                return null;
            }
            Uri uri = onMapIntentToUri(intent);
            Bundle b = new Bundle();
            if (uri != null) {
                b.putString(EXTRA_SLICE, uri.toString());
            }
            return b;
        }
        if (METHOD_PIN.equals(method)) {
            Uri uri = readUriExtra(extras, EXTRA_BIND_URI);
            if (uri != null) {
                onSlicePinned(uri);
            }
            return new Bundle();
        }
        if (METHOD_UNPIN.equals(method)) {
            Uri uri = readUriExtra(extras, EXTRA_BIND_URI);
            if (uri != null) {
                onSliceUnpinned(uri);
            }
            return new Bundle();
        }
        if (METHOD_GET_DESCENDANTS.equals(method)) {
            Uri uri = readUriExtra(extras, EXTRA_BIND_URI);
            Bundle b = new Bundle();
            if (uri != null) {
                Collection<Uri> descendants = onGetSliceDescendants(uri);
                ArrayList<String> serialized = new ArrayList<String>();
                if (descendants != null) {
                    for (Uri d : descendants) {
                        if (d != null) {
                            serialized.add(d.toString());
                        }
                    }
                }
                b.putStringArrayList(EXTRA_SLICE_DESCENDANTS, serialized);
            }
            return b;
        }
        return super.call(method, arg, extras);
    }

    private static Uri readUriExtra(Bundle extras, String key) {
        Object raw = extras.get(key);
        if (raw instanceof Uri) {
            return (Uri) raw;
        }
        if (raw instanceof String) {
            return Uri.parse((String) raw);
        }
        return null;
    }

    private static Set<SliceSpec> readSpecsExtra(Bundle extras) {
        ArrayList<SliceSpec> list = extras.getParcelableArrayList(EXTRA_SUPPORTED_SPECS);
        if (list == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<SliceSpec>(list));
    }
}
