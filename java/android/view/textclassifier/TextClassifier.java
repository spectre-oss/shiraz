// SPDX-License-Identifier: GPL-3.0-only

package android.view.textclassifier;

import android.os.LocaleList;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface TextClassifier {
    String TYPE_UNKNOWN = "";
    String TYPE_OTHER = "other";
    String TYPE_EMAIL = "email";
    String TYPE_PHONE = "phone";
    String TYPE_ADDRESS = "address";
    String TYPE_URL = "url";
    String TYPE_DATE = "date";
    String TYPE_DATE_TIME = "datetime";
    String TYPE_FLIGHT_NUMBER = "flight";

    String HINT_TEXT_IS_EDITABLE = "android.text_is_editable";
    String HINT_TEXT_IS_NOT_EDITABLE = "android.text_is_not_editable";

    String WIDGET_TYPE_TEXTVIEW = "textview";
    String WIDGET_TYPE_EDITTEXT = "edittext";
    String WIDGET_TYPE_UNSELECTABLE_TEXTVIEW = "nosel-textview";
    String WIDGET_TYPE_WEBVIEW = "webview";
    String WIDGET_TYPE_EDIT_WEBVIEW = "edit-webview";
    String WIDGET_TYPE_CUSTOM_TEXTVIEW = "customview";
    String WIDGET_TYPE_CUSTOM_EDITTEXT = "customedit";
    String WIDGET_TYPE_CUSTOM_UNSELECTABLE_TEXTVIEW = "nosel-customview";
    String WIDGET_TYPE_NOTIFICATION = "notification";
    String WIDGET_TYPE_CLIPBOARD = "clipboard";
    String WIDGET_TYPE_UNKNOWN = "unknown";

    String EXTRA_FROM_TEXT_CLASSIFIER = "android.view.textclassifier.extra.FROM_TEXT_CLASSIFIER";

    TextClassifier NO_OP = new TextClassifier() {
        @Override
        public TextSelection suggestSelection(TextSelection.Request request) {
            Objects.requireNonNull(request);
            return new TextSelection.Builder(request.getStartIndex(), request.getEndIndex())
                    .build();
        }

        @Override
        public TextClassification classifyText(TextClassification.Request request) {
            Objects.requireNonNull(request);
            return TextClassification.EMPTY;
        }

        @Override
        public TextLinks generateLinks(TextLinks.Request request) {
            Objects.requireNonNull(request);
            return new TextLinks.Builder(request.getText().toString()).build();
        }

        @Override
        public String toString() {
            return "TextClassifier.NO_OP";
        }
    };

    default TextSelection suggestSelection(TextSelection.Request request) {
        Objects.requireNonNull(request);
        TextSelection.Builder builder =
                new TextSelection.Builder(request.getStartIndex(), request.getEndIndex());
        String sub = request.getText()
                .subSequence(request.getStartIndex(), request.getEndIndex())
                .toString();
        String entity = HeuristicLinks.classifyExact(sub);
        if (entity != null) {
            builder.setEntityType(entity, 1.0f);
        }
        if (request.shouldIncludeTextClassification()) {
            TextClassification.Request clsReq = new TextClassification.Request.Builder(
                    request.getText(), request.getStartIndex(), request.getEndIndex())
                    .setDefaultLocales(request.getDefaultLocales())
                    .build();
            builder.setTextClassification(classifyText(clsReq));
        }
        return builder.build();
    }

    default TextSelection suggestSelection(
            CharSequence text,
            int selectionStartIndex,
            int selectionEndIndex,
            LocaleList defaultLocales) {
        TextSelection.Request request = new TextSelection.Request.Builder(
                text, selectionStartIndex, selectionEndIndex)
                .setDefaultLocales(defaultLocales)
                .build();
        return suggestSelection(request);
    }

    default TextClassification classifyText(TextClassification.Request request) {
        Objects.requireNonNull(request);
        String sub = request.getText()
                .subSequence(request.getStartIndex(), request.getEndIndex())
                .toString();
        TextClassification.Builder builder = new TextClassification.Builder().setText(sub);
        String entity = HeuristicLinks.classifyExact(sub.trim());
        if (entity != null) {
            builder.setEntityType(entity, 1.0f);
        }
        return builder.build();
    }

    default TextClassification classifyText(
            CharSequence text,
            int startIndex,
            int endIndex,
            LocaleList defaultLocales) {
        TextClassification.Request request = new TextClassification.Request.Builder(
                text, startIndex, endIndex)
                .setDefaultLocales(defaultLocales)
                .build();
        return classifyText(request);
    }

    default TextLinks generateLinks(TextLinks.Request request) {
        Objects.requireNonNull(request);
        return HeuristicLinks.generate(request);
    }

    default int getMaxGenerateLinksTextLength() {
        return 100000;
    }

    default TextLanguage detectLanguage(TextLanguage.Request request) {
        Objects.requireNonNull(request);
        return TextLanguage.EMPTY;
    }

    default ConversationActions suggestConversationActions(ConversationActions.Request request) {
        Objects.requireNonNull(request);
        return new ConversationActions(Collections.<ConversationAction>emptyList(), null);
    }

    default void onSelectionEvent(SelectionEvent event) {
    }

    default void onTextClassifierEvent(TextClassifierEvent event) {
    }

    default void destroy() {
    }

    default boolean isDestroyed() {
        return false;
    }

    final class EntityConfig implements Parcelable {
        private final List<String> mIncludedTypes;
        private final List<String> mExcludedTypes;
        private final List<String> mHints;
        private final boolean mIncludeTypesFromTextClassifier;

        private EntityConfig(
                List<String> includedEntityTypes,
                List<String> excludedEntityTypes,
                List<String> hints,
                boolean includeTypesFromTextClassifier) {
            mIncludedTypes = Objects.requireNonNull(includedEntityTypes);
            mExcludedTypes = Objects.requireNonNull(excludedEntityTypes);
            mHints = Objects.requireNonNull(hints);
            mIncludeTypesFromTextClassifier = includeTypesFromTextClassifier;
        }

        private EntityConfig(Parcel in) {
            mIncludedTypes = new ArrayList<String>();
            String[] inc = in.createStringArray();
            if (inc != null) Collections.addAll(mIncludedTypes, inc);
            mExcludedTypes = new ArrayList<String>();
            String[] exc = in.createStringArray();
            if (exc != null) Collections.addAll(mExcludedTypes, exc);
            mHints = new ArrayList<String>();
            String[] h = in.createStringArray();
            if (h != null) Collections.addAll(mHints, h);
            mIncludeTypesFromTextClassifier = in.readByte() != 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int flags) {
            parcel.writeStringArray(mIncludedTypes.toArray(new String[0]));
            parcel.writeStringArray(mExcludedTypes.toArray(new String[0]));
            parcel.writeStringArray(mHints.toArray(new String[0]));
            parcel.writeByte((byte) (mIncludeTypesFromTextClassifier ? 1 : 0));
        }

        @Deprecated
        public static EntityConfig createWithHints(Collection<String> hints) {
            return new EntityConfig.Builder()
                    .includeTypesFromTextClassifier(true)
                    .setHints(hints)
                    .build();
        }

        @Deprecated
        public static EntityConfig create(
                Collection<String> hints,
                Collection<String> includedEntityTypes,
                Collection<String> excludedEntityTypes) {
            return new EntityConfig.Builder()
                    .setIncludedTypes(includedEntityTypes)
                    .setExcludedTypes(excludedEntityTypes)
                    .setHints(hints)
                    .includeTypesFromTextClassifier(true)
                    .build();
        }

        @Deprecated
        public static EntityConfig createWithExplicitEntityList(Collection<String> entityTypes) {
            return new EntityConfig.Builder()
                    .setIncludedTypes(entityTypes)
                    .includeTypesFromTextClassifier(false)
                    .build();
        }

        public Collection<String> resolveEntityListModifications(Collection<String> entityTypes) {
            Set<String> types = new LinkedHashSet<String>();
            if (mIncludeTypesFromTextClassifier && entityTypes != null) {
                types.addAll(entityTypes);
            }
            types.addAll(mIncludedTypes);
            types.removeAll(mExcludedTypes);
            return Collections.unmodifiableCollection(new ArrayList<String>(types));
        }

        public Collection<String> getHints() {
            return mHints;
        }

        public boolean shouldIncludeTypesFromTextClassifier() {
            return mIncludeTypesFromTextClassifier;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Parcelable.Creator<EntityConfig> CREATOR =
                new Parcelable.Creator<EntityConfig>() {
                    @Override
                    public EntityConfig createFromParcel(Parcel in) {
                        return new EntityConfig(in);
                    }

                    @Override
                    public EntityConfig[] newArray(int size) {
                        return new EntityConfig[size];
                    }
                };

        public static final class Builder {
            private Collection<String> mHints;
            private Collection<String> mExcludedTypes;
            private Collection<String> mIncludedTypes;
            private boolean mIncludeTypesFromTextClassifier = true;

            public Builder() {
            }

            public Builder setIncludedTypes(Collection<String> includedTypes) {
                mIncludedTypes = includedTypes;
                return this;
            }

            public Builder setExcludedTypes(Collection<String> excludedTypes) {
                mExcludedTypes = excludedTypes;
                return this;
            }

            public Builder includeTypesFromTextClassifier(boolean includeTypesFromTextClassifier) {
                mIncludeTypesFromTextClassifier = includeTypesFromTextClassifier;
                return this;
            }

            public Builder setHints(Collection<String> hints) {
                mHints = hints;
                return this;
            }

            public EntityConfig build() {
                return new EntityConfig(
                        mIncludedTypes == null
                                ? Collections.<String>emptyList()
                                : new ArrayList<String>(mIncludedTypes),
                        mExcludedTypes == null
                                ? Collections.<String>emptyList()
                                : new ArrayList<String>(mExcludedTypes),
                        mHints == null
                                ? Collections.<String>emptyList()
                                : Collections.unmodifiableList(new ArrayList<String>(mHints)),
                        mIncludeTypesFromTextClassifier);
            }
        }
    }
}

final class HeuristicLinks {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?:(?:https?|ftp)://|www\\.)[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+");
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9])(?:\\+?[1-9][0-9]{0,2}[\\s.\\-]?)?(?:\\([0-9]{2,4}\\)|[0-9]{2,4})[\\s.\\-]?[0-9]{3,4}[\\s.\\-]?[0-9]{3,4}(?![A-Za-z0-9])");

    private static final List<String> DEFAULT_TYPES = Arrays.asList(
            TextClassifier.TYPE_URL,
            TextClassifier.TYPE_EMAIL,
            TextClassifier.TYPE_PHONE);

    private static final class SpanCandidate {
        final int start;
        final int end;
        final String type;

        SpanCandidate(int start, int end, String type) {
            this.start = start;
            this.end = end;
            this.type = type;
        }
    }

    static String classifyExact(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        if (EMAIL_PATTERN.matcher(s).matches()) {
            return TextClassifier.TYPE_EMAIL;
        }
        if (URL_PATTERN.matcher(s).matches()) {
            return TextClassifier.TYPE_URL;
        }
        if (PHONE_PATTERN.matcher(s).matches() && countDigits(s) >= 7) {
            return TextClassifier.TYPE_PHONE;
        }
        return null;
    }

    private static int countDigits(CharSequence s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                n++;
            }
        }
        return n;
    }

    private static int trimTrailingPunctuation(String text, int start, int end) {
        while (end > start) {
            char c = text.charAt(end - 1);
            if (c == '.' || c == ',' || c == ';' || c == ':' || c == '!' || c == '?' || c == ')') {
                if (c == ')' && text.substring(start, end - 1).indexOf('(') >= 0) {
                    break;
                }
                end--;
            } else {
                break;
            }
        }
        return end;
    }

    static TextLinks generate(TextLinks.Request request) {
        String fullText = request.getText().toString();
        TextLinks.Builder builder = new TextLinks.Builder(fullText);
        TextClassifier.EntityConfig cfg = request.getEntityConfig();
        Set<String> enabled = new HashSet<String>(
                cfg != null ? cfg.resolveEntityListModifications(DEFAULT_TYPES) : DEFAULT_TYPES);
        if (enabled.isEmpty() || fullText.isEmpty()) {
            return builder.build();
        }

        List<SpanCandidate> candidates = new ArrayList<SpanCandidate>();
        if (enabled.contains(TextClassifier.TYPE_EMAIL)) {
            Matcher m = EMAIL_PATTERN.matcher(fullText);
            while (m.find()) {
                candidates.add(new SpanCandidate(m.start(), m.end(), TextClassifier.TYPE_EMAIL));
            }
        }
        if (enabled.contains(TextClassifier.TYPE_URL)) {
            Matcher m = URL_PATTERN.matcher(fullText);
            while (m.find()) {
                int s = m.start();
                int e = trimTrailingPunctuation(fullText, s, m.end());
                if (e > s) {
                    candidates.add(new SpanCandidate(s, e, TextClassifier.TYPE_URL));
                }
            }
        }
        if (enabled.contains(TextClassifier.TYPE_PHONE)) {
            Matcher m = PHONE_PATTERN.matcher(fullText);
            while (m.find()) {
                String match = fullText.substring(m.start(), m.end());
                int digits = countDigits(match);
                if (digits >= 7 && digits <= 15) {
                    candidates.add(new SpanCandidate(
                            m.start(), m.end(), TextClassifier.TYPE_PHONE));
                }
            }
        }

        Collections.sort(candidates, new java.util.Comparator<SpanCandidate>() {
            @Override
            public int compare(SpanCandidate a, SpanCandidate b) {
                if (a.start != b.start) {
                    return Integer.compare(a.start, b.start);
                }
                return Integer.compare(b.end - b.start, a.end - a.start);
            }
        });

        int lastEnd = -1;
        for (int i = 0; i < candidates.size(); i++) {
            SpanCandidate c = candidates.get(i);
            if (c.start >= lastEnd) {
                builder.addLink(
                        c.start,
                        c.end,
                        Collections.singletonMap(c.type, Float.valueOf(1.0f)));
                lastEnd = c.end;
            }
        }
        return builder.build();
    }
}
