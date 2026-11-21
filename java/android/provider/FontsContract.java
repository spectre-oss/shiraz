// SPDX-License-Identifier: GPL-3.0-only

package android.provider;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.fonts.FontVariationAxis;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Handler;
import java.util.ArrayList;
import java.util.Objects;

@Deprecated
public class FontsContract {
    private FontsContract() {
    }

    public static final class Columns implements BaseColumns {
        public static final String FILE_ID = "file_id";
        public static final String TTC_INDEX = "font_ttc_index";
        public static final String VARIATION_SETTINGS = "font_variation_settings";
        public static final String WEIGHT = "font_weight";
        public static final String ITALIC = "font_italic";
        public static final String RESULT_CODE = "result_code";

        public static final int RESULT_CODE_OK = 0;
        public static final int RESULT_CODE_FONT_NOT_FOUND = 1;
        public static final int RESULT_CODE_FONT_UNAVAILABLE = 2;
        public static final int RESULT_CODE_MALFORMED_QUERY = 3;

        private Columns() {
        }
    }

    public static class FontInfo {
        private final Uri mUri;
        private final int mTtcIndex;
        private final FontVariationAxis[] mAxes;
        private final int mWeight;
        private final boolean mItalic;
        private final int mResultCode;

        FontInfo(
                Uri uri,
                int ttcIndex,
                FontVariationAxis[] axes,
                int weight,
                boolean italic,
                int resultCode) {
            mUri = Objects.requireNonNull(uri);
            mTtcIndex = ttcIndex;
            mAxes = axes;
            mWeight = weight;
            mItalic = italic;
            mResultCode = resultCode;
        }

        public Uri getUri() {
            return mUri;
        }

        public int getTtcIndex() {
            return mTtcIndex;
        }

        public FontVariationAxis[] getAxes() {
            return mAxes;
        }

        public int getWeight() {
            return mWeight;
        }

        public boolean isItalic() {
            return mItalic;
        }

        public int getResultCode() {
            return mResultCode;
        }
    }

    public static class FontFamilyResult {
        public static final int STATUS_OK = 0;
        public static final int STATUS_WRONG_CERTIFICATES = 1;
        public static final int STATUS_UNEXPECTED_DATA_PROVIDED = 2;
        public static final int STATUS_REJECTED = 3;

        private final int mStatusCode;
        private final FontInfo[] mFonts;

        FontFamilyResult(int statusCode, FontInfo[] fonts) {
            mStatusCode = statusCode;
            mFonts = fonts;
        }

        public int getStatusCode() {
            return mStatusCode;
        }

        public FontInfo[] getFonts() {
            return mFonts;
        }
    }

    public static class FontRequestCallback {
        public static final int FAIL_REASON_PROVIDER_NOT_FOUND = -1;
        public static final int FAIL_REASON_WRONG_CERTIFICATES = -2;
        public static final int FAIL_REASON_FONT_LOAD_ERROR = -3;
        public static final int FAIL_REASON_FONT_NOT_FOUND = 1;
        public static final int FAIL_REASON_FONT_UNAVAILABLE = 2;
        public static final int FAIL_REASON_MALFORMED_QUERY = 3;

        public FontRequestCallback() {
        }

        public void onTypefaceRetrieved(Typeface typeface) {
        }

        public void onTypefaceRequestFailed(int reason) {
        }
    }

    public static void requestFonts(
            final Context context,
            final FontRequest request,
            final Handler handler,
            final CancellationSignal cancellationSignal,
            final FontRequestCallback callback) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(callback, "callback");
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (cancellationSignal != null && cancellationSignal.isCanceled()) {
                    return;
                }
                final FontFamilyResult result;
                try {
                    result = fetchFonts(context, cancellationSignal, request);
                } catch (PackageManager.NameNotFoundException e) {
                    callback.onTypefaceRequestFailed(
                            FontRequestCallback.FAIL_REASON_PROVIDER_NOT_FOUND);
                    return;
                }
                if (result.getStatusCode() != FontFamilyResult.STATUS_OK) {
                    if (result.getStatusCode() == FontFamilyResult.STATUS_WRONG_CERTIFICATES) {
                        callback.onTypefaceRequestFailed(
                                FontRequestCallback.FAIL_REASON_WRONG_CERTIFICATES);
                    } else {
                        callback.onTypefaceRequestFailed(
                                FontRequestCallback.FAIL_REASON_FONT_LOAD_ERROR);
                    }
                    return;
                }
                FontInfo[] fonts = result.getFonts();
                if (fonts == null || fonts.length == 0) {
                    callback.onTypefaceRequestFailed(
                            FontRequestCallback.FAIL_REASON_FONT_NOT_FOUND);
                    return;
                }
                for (FontInfo font : fonts) {
                    if (font.getResultCode() != Columns.RESULT_CODE_OK) {
                        int rc = font.getResultCode();
                        callback.onTypefaceRequestFailed(
                                rc < 0 ? FontRequestCallback.FAIL_REASON_FONT_LOAD_ERROR : rc);
                        return;
                    }
                }
                Typeface tf = buildTypeface(context, cancellationSignal, fonts);
                if (tf == null) {
                    callback.onTypefaceRequestFailed(
                            FontRequestCallback.FAIL_REASON_FONT_LOAD_ERROR);
                } else {
                    callback.onTypefaceRetrieved(tf);
                }
            }
        });
    }

    public static FontFamilyResult fetchFonts(
            Context context, CancellationSignal cancellationSignal, FontRequest request)
            throws PackageManager.NameNotFoundException {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(request, "request");
        if (cancellationSignal != null) {
            cancellationSignal.throwIfCanceled();
        }
        PackageManager pm = context.getPackageManager();
        if (pm != null) {
            ProviderInfo providerInfo =
                    pm.resolveContentProvider(request.getProviderAuthority(), 0);
            if (providerInfo == null) {
                throw new PackageManager.NameNotFoundException(
                        "No package found for authority: " + request.getProviderAuthority());
            }
            if (providerInfo.packageName != null
                    && !providerInfo.packageName.equals(request.getProviderPackage())) {
                throw new PackageManager.NameNotFoundException(
                        "Found content provider " + request.getProviderAuthority()
                                + ", but package was not " + request.getProviderPackage());
            }
        }
        Uri uri = new Uri.Builder()
                .scheme("content")
                .authority(request.getProviderAuthority())
                .build();
        Uri fileBaseUri = new Uri.Builder()
                .scheme("content")
                .authority(request.getProviderAuthority())
                .appendPath("file")
                .build();
        ArrayList<FontInfo> list = new ArrayList<FontInfo>();
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    uri,
                    new String[] {
                        Columns._ID,
                        Columns.FILE_ID,
                        Columns.TTC_INDEX,
                        Columns.VARIATION_SETTINGS,
                        Columns.WEIGHT,
                        Columns.ITALIC,
                        Columns.RESULT_CODE
                    },
                    "query = ?",
                    new String[] {request.getQuery()},
                    null,
                    cancellationSignal);
            if (cursor != null && cursor.getCount() > 0) {
                int resultCodeCol = cursor.getColumnIndex(Columns.RESULT_CODE);
                int idCol = cursor.getColumnIndex(Columns._ID);
                int fileIdCol = cursor.getColumnIndex(Columns.FILE_ID);
                int ttcCol = cursor.getColumnIndex(Columns.TTC_INDEX);
                int varCol = cursor.getColumnIndex(Columns.VARIATION_SETTINGS);
                int weightCol = cursor.getColumnIndex(Columns.WEIGHT);
                int italicCol = cursor.getColumnIndex(Columns.ITALIC);
                while (cursor.moveToNext()) {
                    int resultCode = resultCodeCol != -1
                            ? cursor.getInt(resultCodeCol) : Columns.RESULT_CODE_OK;
                    int ttcIndex = ttcCol != -1 ? cursor.getInt(ttcCol) : 0;
                    String variation = varCol != -1 ? cursor.getString(varCol) : null;
                    Uri fileUri;
                    if (fileIdCol == -1 || cursor.isNull(fileIdCol)) {
                        long id = idCol != -1 ? cursor.getLong(idCol) : 0L;
                        fileUri = ContentUris.withAppendedId(uri, id);
                    } else {
                        fileUri = ContentUris.withAppendedId(fileBaseUri, cursor.getLong(fileIdCol));
                    }
                    int weight = weightCol != -1 ? cursor.getInt(weightCol) : 400;
                    boolean italic = italicCol != -1 && cursor.getInt(italicCol) == 1;
                    FontVariationAxis[] axes = variation != null
                            ? FontVariationAxis.fromFontVariationSettings(variation) : null;
                    list.add(new FontInfo(fileUri, ttcIndex, axes, weight, italic, resultCode));
                }
            } else if (cursor == null && pm == null) {
                throw new PackageManager.NameNotFoundException(
                        "No provider found for authority: " + request.getProviderAuthority());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return new FontFamilyResult(
                FontFamilyResult.STATUS_OK, list.toArray(new FontInfo[list.size()]));
    }

    public static Typeface buildTypeface(
            Context context, CancellationSignal cancellationSignal, FontInfo[] fonts) {
        if (cancellationSignal != null && cancellationSignal.isCanceled()) {
            return null;
        }
        if (fonts == null || fonts.length == 0) {
            return null;
        }
        FontInfo primary = fonts[0];
        if (primary == null) {
            return null;
        }
        int weight = Math.max(1, Math.min(1000, primary.getWeight()));
        return Typeface.create(Typeface.DEFAULT, weight, primary.isItalic());
    }
}
