// SPDX-License-Identifier: GPL-3.0-only
import android.app.Person;
import android.app.admin.DevicePolicyIdentifiers;
import android.app.appsearch.AppSearchBatchResult;
import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchResult;
import android.app.appsearch.AppSearchSchema;
import android.app.appsearch.AppSearchSession;
import android.app.appsearch.BatchResultCallback;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.PutDocumentsRequest;
import android.app.appsearch.SearchResult;
import android.app.appsearch.SearchResults;
import android.app.appsearch.SearchSpec;
import android.app.appsearch.SetSchemaRequest;
import android.app.appsearch.SetSchemaResponse;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.LocusId;
import android.content.RestrictionEntry;
import android.content.UriRelativeFilter;
import android.content.UriRelativeFilterGroup;
import android.content.pm.ApkChecksum;
import android.content.pm.ShortcutInfo;
import android.content.res.FontScaleConverter;
import android.content.res.Resources;
import android.icu.lang.UCharacter;
import android.net.InetAddresses;
import android.net.Uri;
import android.net.UrlQuerySanitizer;
import android.os.Parcel;
import android.provider.ContactsContract;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.telephony.CellSignalStrengthLte;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.text.PrecomputedText;
import android.text.TextPaint;
import android.text.format.Time;
import android.text.style.TtsSpan;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.widget.MultiAutoCompleteTextView;
import android.widget.RatingBar;
import android.widget.Switch;
import java.io.File;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class FrameworkMilestoneF6Smoke {
    private static final Executor DIRECT_EXECUTOR = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Assertion failed: " + message);
        }
    }

    public static void main(String[] args) throws Exception {
        final File tmpDir = new File(System.getProperty("java.io.tmpdir", "/tmp"));
        final Resources.Theme theme = Resources.getSystem().newTheme();
        Context context = new ContextWrapper(null) {
            @Override
            public Context getApplicationContext() {
                return this;
            }

            @Override
            public String getPackageName() {
                return "shiraz.smoke.f6";
            }

            @Override
            public Resources getResources() {
                return Resources.getSystem();
            }

            @Override
            public Resources.Theme getTheme() {
                return theme;
            }

            @Override
            public File getFilesDir() {
                return tmpDir;
            }

            @Override
            public File getCacheDir() {
                return tmpDir;
            }
        };

        // 1. android.content + font scale + URI relative filter + Parcelables
        FontScaleConverter converter = FontScaleConverter.forScale(1.5f);
        require(converter != null, "FontScaleConverter for 1.5x");
        float dp = converter.convertSpToDp(20f);
        float spBack = converter.convertDpToSp(dp);
        require(Math.abs(spBack - 20f) < 0.5f, "FontScaleConverter round-trip: " + spBack);

        UriRelativeFilterGroup group = new UriRelativeFilterGroup(UriRelativeFilterGroup.ACTION_ALLOW);
        group.addUriRelativeFilter(new UriRelativeFilter(
                UriRelativeFilter.PATH, android.os.PatternMatcher.PATTERN_PREFIX, "/items"));
        require(group.matchData(Uri.parse("https://shiraz.test/items/42")), "UriRelativeFilterGroup allow");
        require(!group.matchData(Uri.parse("https://shiraz.test/other")), "UriRelativeFilterGroup reject");

        ApkChecksum checksum = new ApkChecksum("base.apk", 32, new byte[]{1, 2, 3, 4});
        Parcel p = Parcel.obtain();
        checksum.writeToParcel(p, 0);
        p.setDataPosition(0);
        ApkChecksum checksumOut = ApkChecksum.CREATOR.createFromParcel(p);
        require("base.apk".equals(checksumOut.getSplitName()) && checksumOut.getType() == 32, "ApkChecksum parcel");
        p.recycle();

        RestrictionEntry child = new RestrictionEntry("flag", true);
        RestrictionEntry bundleEntry = RestrictionEntry.createBundleEntry("parent", new RestrictionEntry[]{child});
        p = Parcel.obtain();
        bundleEntry.writeToParcel(p, 0);
        p.setDataPosition(0);
        RestrictionEntry bundleOut = RestrictionEntry.CREATOR.createFromParcel(p);
        require(bundleOut.getRestrictions() != null && bundleOut.getRestrictions().length == 1, "RestrictionEntry parcel");
        p.recycle();

        Person person = new Person.Builder().setName("Cyrus").setKey("cyrus-1").setImportant(true).build();
        ShortcutInfo shortcut = new ShortcutInfo.Builder(context, "sc-1")
                .setShortLabel("Chat")
                .setLocusId(new LocusId("locus-chat"))
                .setPerson(person)
                .build();
        p = Parcel.obtain();
        shortcut.writeToParcel(p, 0);
        p.setDataPosition(0);
        ShortcutInfo shortcutOut = ShortcutInfo.CREATOR.createFromParcel(p);
        require("sc-1".equals(shortcutOut.getId()) && shortcutOut.getLocusId() != null, "ShortcutInfo parcel");
        p.recycle();

        // 2. android.app.appsearch + android.app.admin
        String policyId = DevicePolicyIdentifiers.getIdentifierForUserRestriction("no_install_apps");
        require("userRestriction_no_install_apps".equals(policyId), "DevicePolicyIdentifiers: " + policyId);

        AppSearchManager appSearch = new AppSearchManager(context);
        final AppSearchSession[] sessionHolder = new AppSearchSession[1];
        appSearch.createSearchSession(
                new AppSearchManager.SearchContext.Builder("notes_db").build(),
                DIRECT_EXECUTOR,
                new Consumer<AppSearchResult<AppSearchSession>>() {
                    @Override
                    public void accept(AppSearchResult<AppSearchSession> res) {
                        sessionHolder[0] = res.getResultValue();
                    }
                });
        AppSearchSession session = sessionHolder[0];
        require(session != null, "AppSearchSession created");

        AppSearchSchema noteSchema = new AppSearchSchema.Builder("Note")
                .addProperty(new AppSearchSchema.StringPropertyConfig.Builder("body")
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                        .setIndexingType(AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                        .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .build())
                .build();
        final boolean[] schemaSet = new boolean[1];
        session.setSchema(
                new SetSchemaRequest.Builder().addSchemas(noteSchema).build(),
                DIRECT_EXECUTOR,
                DIRECT_EXECUTOR,
                new Consumer<AppSearchResult<SetSchemaResponse>>() {
                    @Override
                    public void accept(AppSearchResult<SetSchemaResponse> res) {
                        schemaSet[0] = res.isSuccess();
                    }
                });
        require(schemaSet[0], "AppSearch setSchema");

        GenericDocument doc = new GenericDocument.Builder<GenericDocument.Builder<?>>("ns", "doc-1", "Note")
                .setPropertyString("body", "Shiraz runs Android bytecode natively")
                .build();
        final boolean[] putOk = new boolean[1];
        session.put(
                new PutDocumentsRequest.Builder().addGenericDocuments(doc).build(),
                DIRECT_EXECUTOR,
                new BatchResultCallback<String, Void>() {
                    @Override
                    public void onResult(AppSearchBatchResult<String, Void> result) {
                        putOk[0] = result.isSuccess();
                    }

                    @Override
                    public void onSystemError(Throwable throwable) {
                    }
                });
        require(putOk[0], "AppSearch put");

        SearchResults results = session.search(
                "byt",
                new SearchSpec.Builder()
                        .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
                        .setSnippetCount(1)
                        .build());
        final List<SearchResult> pageHolder = new ArrayList<SearchResult>();
        results.getNextPage(DIRECT_EXECUTOR, new Consumer<AppSearchResult<List<SearchResult>>>() {
            @Override
            public void accept(AppSearchResult<List<SearchResult>> res) {
                if (res.isSuccess() && res.getResultValue() != null) {
                    pageHolder.addAll(res.getResultValue());
                }
            }
        });
        require(pageHolder.size() == 1, "AppSearch prefix search hit");
        require("doc-1".equals(pageHolder.get(0).getGenericDocument().getId()), "AppSearch hit id");
        require(!pageHolder.get(0).getMatchInfos().isEmpty(), "AppSearch MatchInfo snippet");
        results.close();
        session.close();

        // 3. android.telephony + android.text
        TtsSpan tts = new TtsSpan.CardinalBuilder(42L).build();
        require(TtsSpan.TYPE_CARDINAL.equals(tts.getType()), "TtsSpan cardinal");
        String e164 = PhoneNumberUtils.formatNumberToE164("650-555-1234", "US");
        require("+16505551234".equals(e164), "PhoneNumberUtils E164: " + e164);
        require(CellSignalStrengthLte.CREATOR.newArray(1).length == 1, "CellSignalStrengthLte CREATOR");
        require("LOCAL".equals(android.telephony.DisconnectCause.toString(android.telephony.DisconnectCause.LOCAL)), "DisconnectCause.toString");
        ArrayList<String> parts = SmsManager.getDefault().divideMessage("Hello Shiraz SMS");
        require(parts.size() == 1 && "Hello Shiraz SMS".equals(parts.get(0)), "SmsManager divideMessage");

        Rfc822Token[] tokens = Rfc822Tokenizer.tokenize("Shiraz Dev <dev@shiraz.example>");
        require(tokens.length == 1 && "dev@shiraz.example".equals(tokens[0].getAddress()), "Rfc822Tokenizer");
        Time rfcTime = new Time();
        require(rfcTime.parse3339("2026-11-21T12:00:00Z"), "Time.parse3339");
        PrecomputedText pre = PrecomputedText.create(
                "Hello PrecomputedText",
                new PrecomputedText.Params.Builder(new TextPaint()).build());
        require(pre.length() == "Hello PrecomputedText".length(), "PrecomputedText length");

        // 4. android.widget
        Switch sw = new Switch(context);
        sw.setChecked(true);
        sw.toggle();
        require(!sw.isChecked(), "Switch toggle");
        RatingBar rb = new RatingBar(context);
        rb.setNumStars(5);
        rb.setStepSize(0.5f);
        rb.setRating(3.5f);
        require(Math.abs(rb.getRating() - 3.5f) < 0.01f, "RatingBar rating");
        MultiAutoCompleteTextView.CommaTokenizer comma = new MultiAutoCompleteTextView.CommaTokenizer();
        require("alpha, ".equals(comma.terminateToken("alpha").toString()), "CommaTokenizer");

        // 5. android.provider + android.net
        DocumentsContract.Path docPath = new DocumentsContract.Path("root-1", Arrays.asList("dir", "leaf"));
        p = Parcel.obtain();
        docPath.writeToParcel(p, 0);
        p.setDataPosition(0);
        DocumentsContract.Path docPathOut = DocumentsContract.Path.CREATOR.createFromParcel(p);
        require("root-1".equals(docPathOut.getRootId()) && docPathOut.getPath().size() == 2, "DocumentsContract.Path");
        p.recycle();
        require(MediaStore.Audio.keyFor("The Beatles") != null, "MediaStore.Audio.keyFor");
        require(ContactsContract.SimAccount.CREATOR.newArray(2).length == 2, "ContactsContract.SimAccount CREATOR");

        require(InetAddresses.isNumericAddress("192.0.2.10"), "InetAddresses IPv4");
        InetAddress parsedAddr = InetAddresses.parseNumericAddress("192.0.2.10");
        require("192.0.2.10".equals(parsedAddr.getHostAddress()), "InetAddresses parseNumericAddress");
        UrlQuerySanitizer sanitizer = new UrlQuerySanitizer();
        sanitizer.setAllowUnregisteredParamaters(true);
        sanitizer.setUnregisteredParameterValueSanitizer(UrlQuerySanitizer.getAllButNulLegal());
        sanitizer.parseQuery("mode=fast&count=3");
        require("fast".equals(sanitizer.getValue("mode")), "UrlQuerySanitizer");

        // 6. Special Cleaner + Flow + ICU inner classes
        final boolean[] cleaned = new boolean[1];
        Class<?> sysCleanerCls = Class.forName("android.system.SystemCleaner");
        Object cleanerObj = sysCleanerCls.getMethod("cleaner").invoke(null);
        Object cleanableObj = cleanerObj.getClass()
                .getMethod("register", Object.class, Runnable.class)
                .invoke(cleanerObj, new Object(), new Runnable() {
                    @Override
                    public void run() {
                        cleaned[0] = true;
                    }
                });
        Class.forName("java.lang.ref.Cleaner$Cleanable").getMethod("clean").invoke(cleanableObj);
        require(cleaned[0], "SystemCleaner clean()");

        Class<?> flowCls = Class.forName("java.util.concurrent.Flow");
        int bufSize = ((Integer) flowCls.getMethod("defaultBufferSize").invoke(null)).intValue();
        require(bufSize == 256, "Flow.defaultBufferSize()");
        require(Class.forName("android.service.controls.ControlsProviderService") != null, "ControlsProviderService loaded");

        try {
            require(UCharacter.isDigit('5'), "core-libart UCharacter outer intact");
        } catch (LinkageError ignoredOnOpenJdkHost) {
            // Host OpenJDK does not ship libicujni native bindings for UCharacter.
        }
        Class<?> prefixCls = Class.forName("android.icu.util.MeasureUnit$MeasurePrefix");
        Object kibi = Enum.valueOf((Class) prefixCls, "KIBI");
        Method getBase = prefixCls.getMethod("getBase");
        Method getPower = prefixCls.getMethod("getPower");
        require(((Integer) getBase.invoke(kibi)).intValue() == 1024, "MeasurePrefix.KIBI base");
        require(((Integer) getPower.invoke(kibi)).intValue() == 1, "MeasurePrefix.KIBI power");
        Class<?> voCls = Class.forName("android.icu.lang.UCharacter$VerticalOrientation");
        require(voCls.getField("UPRIGHT").getInt(null) == 3, "UCharacter.VerticalOrientation.UPRIGHT");

        System.out.println("FRAMEWORK_F6_OK");
    }
}
