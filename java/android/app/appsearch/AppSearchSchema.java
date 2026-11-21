// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class AppSearchSchema implements Parcelable {
    private final String mSchemaType;
    private final List<PropertyConfig> mProperties;
    private final List<String> mParentTypes;

    private AppSearchSchema(
            String schemaType, List<PropertyConfig> properties, List<String> parentTypes) {
        mSchemaType = Objects.requireNonNull(schemaType);
        mProperties = Collections.unmodifiableList(new ArrayList<PropertyConfig>(properties));
        mParentTypes = Collections.unmodifiableList(new ArrayList<String>(parentTypes));
    }

    AppSearchSchema(Parcel in) {
        mSchemaType = in.readString();
        int propCount = in.readInt();
        List<PropertyConfig> props = new ArrayList<PropertyConfig>(Math.max(0, propCount));
        for (int i = 0; i < propCount; i++) {
            props.add(PropertyConfig.readFromParcel(in));
        }
        mProperties = Collections.unmodifiableList(props);
        int parentCount = in.readInt();
        List<String> parents = new ArrayList<String>(Math.max(0, parentCount));
        for (int i = 0; i < parentCount; i++) {
            parents.add(in.readString());
        }
        mParentTypes = Collections.unmodifiableList(parents);
    }

    @Override
    public String toString() {
        return "AppSearchSchema{schemaType='" + mSchemaType
                + "', properties=" + mProperties
                + ", parentTypes=" + mParentTypes + "}";
    }

    public String getSchemaType() {
        return mSchemaType;
    }

    public List<PropertyConfig> getProperties() {
        return mProperties;
    }

    public List<String> getParentTypes() {
        return mParentTypes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AppSearchSchema)) return false;
        AppSearchSchema that = (AppSearchSchema) other;
        return Objects.equals(mSchemaType, that.mSchemaType)
                && Objects.equals(mProperties, that.mProperties)
                && Objects.equals(mParentTypes, that.mParentTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSchemaType, mProperties, mParentTypes);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mSchemaType);
        dest.writeInt(mProperties.size());
        for (PropertyConfig p : mProperties) {
            p.writeToParcel(dest);
        }
        dest.writeInt(mParentTypes.size());
        for (String pt : mParentTypes) {
            dest.writeString(pt);
        }
    }

    @Override
    public final int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<AppSearchSchema> CREATOR =
            new Parcelable.Creator<AppSearchSchema>() {
                @Override
                public AppSearchSchema createFromParcel(Parcel in) {
                    return new AppSearchSchema(in);
                }

                @Override
                public AppSearchSchema[] newArray(int size) {
                    return new AppSearchSchema[size];
                }
            };

    public static final class Builder {
        private final String mSchemaType;
        private final List<PropertyConfig> mProperties = new ArrayList<PropertyConfig>();
        private final Set<String> mPropertyNames = new LinkedHashSet<String>();
        private final Set<String> mParentTypes = new LinkedHashSet<String>();

        public Builder(String schemaType) {
            mSchemaType = Objects.requireNonNull(schemaType);
        }

        public Builder addProperty(PropertyConfig propertyConfig) {
            Objects.requireNonNull(propertyConfig);
            if (!mPropertyNames.add(propertyConfig.getName())) {
                throw new IllegalArgumentException(
                        "Property defined more than once: " + propertyConfig.getName());
            }
            mProperties.add(propertyConfig);
            return this;
        }

        public Builder addParentType(String parentSchemaType) {
            Objects.requireNonNull(parentSchemaType);
            mParentTypes.add(parentSchemaType);
            return this;
        }

        public AppSearchSchema build() {
            return new AppSearchSchema(
                    mSchemaType, mProperties, new ArrayList<String>(mParentTypes));
        }
    }

    public abstract static class PropertyConfig {
        public static final int CARDINALITY_REPEATED = 1;
        public static final int CARDINALITY_OPTIONAL = 2;
        public static final int CARDINALITY_REQUIRED = 3;

        static final int TYPE_STRING = 1;
        static final int TYPE_LONG = 2;
        static final int TYPE_DOUBLE = 3;
        static final int TYPE_BOOLEAN = 4;
        static final int TYPE_BYTES = 5;
        static final int TYPE_DOCUMENT = 6;

        final String mName;
        final int mDataType;
        final int mCardinality;

        PropertyConfig(String name, int dataType, int cardinality) {
            mName = Objects.requireNonNull(name);
            if (cardinality < CARDINALITY_REPEATED || cardinality > CARDINALITY_REQUIRED) {
                throw new IllegalArgumentException("Invalid cardinality: " + cardinality);
            }
            mDataType = dataType;
            mCardinality = cardinality;
        }

        @Override
        public String toString() {
            return "PropertyConfig{name='" + mName + "', cardinality=" + mCardinality + "}";
        }

        public String getName() {
            return mName;
        }

        public int getCardinality() {
            return mCardinality;
        }

        abstract void writeToParcel(Parcel dest);

        static PropertyConfig readFromParcel(Parcel in) {
            int type = in.readInt();
            String name = in.readString();
            int card = in.readInt();
            switch (type) {
                case TYPE_STRING:
                    return new StringPropertyConfig(name, card, in.readInt(), in.readInt(), in.readInt());
                case TYPE_LONG:
                    return new LongPropertyConfig(name, card, in.readInt());
                case TYPE_DOUBLE:
                    return new DoublePropertyConfig(name, card);
                case TYPE_BOOLEAN:
                    return new BooleanPropertyConfig(name, card);
                case TYPE_BYTES:
                    return new BytesPropertyConfig(name, card);
                case TYPE_DOCUMENT: {
                    String schema = in.readString();
                    boolean indexNested = in.readInt() != 0;
                    int count = in.readInt();
                    List<String> nested = new ArrayList<String>(Math.max(0, count));
                    for (int i = 0; i < count; i++) nested.add(in.readString());
                    return new DocumentPropertyConfig(name, card, schema, indexNested, nested);
                }
                default:
                    throw new IllegalArgumentException("Unknown property type: " + type);
            }
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            PropertyConfig that = (PropertyConfig) other;
            return mDataType == that.mDataType
                    && mCardinality == that.mCardinality
                    && Objects.equals(mName, that.mName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mName, Integer.valueOf(mDataType), Integer.valueOf(mCardinality));
        }
    }

    public static final class StringPropertyConfig extends PropertyConfig {
        public static final int INDEXING_TYPE_NONE = 0;
        public static final int INDEXING_TYPE_EXACT_TERMS = 1;
        public static final int INDEXING_TYPE_PREFIXES = 2;

        public static final int TOKENIZER_TYPE_NONE = 0;
        public static final int TOKENIZER_TYPE_PLAIN = 1;
        public static final int TOKENIZER_TYPE_VERBATIM = 2;
        public static final int TOKENIZER_TYPE_RFC822 = 3;

        public static final int JOINABLE_VALUE_TYPE_NONE = 0;
        public static final int JOINABLE_VALUE_TYPE_QUALIFIED_ID = 1;

        private final int mIndexingType;
        private final int mTokenizerType;
        private final int mJoinableValueType;

        StringPropertyConfig(
                String name,
                int cardinality,
                int indexingType,
                int tokenizerType,
                int joinableValueType) {
            super(name, TYPE_STRING, cardinality);
            mIndexingType = indexingType;
            mTokenizerType = tokenizerType;
            mJoinableValueType = joinableValueType;
        }

        public int getIndexingType() {
            return mIndexingType;
        }

        public int getTokenizerType() {
            return mTokenizerType;
        }

        public int getJoinableValueType() {
            return mJoinableValueType;
        }

        @Override
        void writeToParcel(Parcel dest) {
            dest.writeInt(TYPE_STRING);
            dest.writeString(mName);
            dest.writeInt(mCardinality);
            dest.writeInt(mIndexingType);
            dest.writeInt(mTokenizerType);
            dest.writeInt(mJoinableValueType);
        }

        @Override
        public boolean equals(Object other) {
            if (!super.equals(other)) return false;
            StringPropertyConfig that = (StringPropertyConfig) other;
            return mIndexingType == that.mIndexingType
                    && mTokenizerType == that.mTokenizerType
                    && mJoinableValueType == that.mJoinableValueType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    Integer.valueOf(super.hashCode()),
                    Integer.valueOf(mIndexingType),
                    Integer.valueOf(mTokenizerType),
                    Integer.valueOf(mJoinableValueType));
        }

        public static final class Builder {
            private final String mPropertyName;
            private int mCardinality = CARDINALITY_OPTIONAL;
            private int mIndexingType = INDEXING_TYPE_NONE;
            private int mTokenizerType = TOKENIZER_TYPE_NONE;
            private int mJoinableValueType = JOINABLE_VALUE_TYPE_NONE;

            public Builder(String propertyName) {
                mPropertyName = Objects.requireNonNull(propertyName);
            }

            public Builder setCardinality(int cardinality) {
                if (cardinality < CARDINALITY_REPEATED || cardinality > CARDINALITY_REQUIRED) {
                    throw new IllegalArgumentException("Invalid cardinality: " + cardinality);
                }
                mCardinality = cardinality;
                return this;
            }

            public Builder setIndexingType(int indexingType) {
                if (indexingType < INDEXING_TYPE_NONE || indexingType > INDEXING_TYPE_PREFIXES) {
                    throw new IllegalArgumentException("Invalid indexingType: " + indexingType);
                }
                mIndexingType = indexingType;
                return this;
            }

            public Builder setTokenizerType(int tokenizerType) {
                if (tokenizerType < TOKENIZER_TYPE_NONE || tokenizerType > TOKENIZER_TYPE_RFC822) {
                    throw new IllegalArgumentException("Invalid tokenizerType: " + tokenizerType);
                }
                mTokenizerType = tokenizerType;
                return this;
            }

            public Builder setJoinableValueType(int joinableValueType) {
                if (joinableValueType < JOINABLE_VALUE_TYPE_NONE
                        || joinableValueType > JOINABLE_VALUE_TYPE_QUALIFIED_ID) {
                    throw new IllegalArgumentException(
                            "Invalid joinableValueType: " + joinableValueType);
                }
                mJoinableValueType = joinableValueType;
                return this;
            }

            public StringPropertyConfig build() {
                if (mTokenizerType == TOKENIZER_TYPE_NONE) {
                    if (mIndexingType != INDEXING_TYPE_NONE) {
                        throw new IllegalStateException(
                                "Cannot set TOKENIZER_TYPE_NONE with an indexing type other than INDEXING_TYPE_NONE");
                    }
                } else if (mIndexingType == INDEXING_TYPE_NONE) {
                    throw new IllegalStateException(
                            "Cannot set TOKENIZER_TYPE with INDEXING_TYPE_NONE");
                }
                if (mJoinableValueType == JOINABLE_VALUE_TYPE_QUALIFIED_ID
                        && mCardinality == CARDINALITY_REPEATED) {
                    throw new IllegalStateException(
                            "Cannot set JOINABLE_VALUE_TYPE_QUALIFIED_ID with CARDINALITY_REPEATED");
                }
                return new StringPropertyConfig(
                        mPropertyName,
                        mCardinality,
                        mIndexingType,
                        mTokenizerType,
                        mJoinableValueType);
            }
        }
    }

    public static final class LongPropertyConfig extends PropertyConfig {
        public static final int INDEXING_TYPE_NONE = 0;
        public static final int INDEXING_TYPE_RANGE = 1;

        private final int mIndexingType;

        LongPropertyConfig(String name, int cardinality, int indexingType) {
            super(name, TYPE_LONG, cardinality);
            mIndexingType = indexingType;
        }

        public int getIndexingType() {
            return mIndexingType;
        }

        @Override
        void writeToParcel(Parcel dest) {
            dest.writeInt(TYPE_LONG);
            dest.writeString(mName);
            dest.writeInt(mCardinality);
            dest.writeInt(mIndexingType);
        }

        @Override
        public boolean equals(Object other) {
            if (!super.equals(other)) return false;
            return mIndexingType == ((LongPropertyConfig) other).mIndexingType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Integer.valueOf(super.hashCode()), Integer.valueOf(mIndexingType));
        }

        public static final class Builder {
            private final String mPropertyName;
            private int mCardinality = CARDINALITY_OPTIONAL;
            private int mIndexingType = INDEXING_TYPE_NONE;

            public Builder(String propertyName) {
                mPropertyName = Objects.requireNonNull(propertyName);
            }

            public Builder setCardinality(int cardinality) {
                if (cardinality < CARDINALITY_REPEATED || cardinality > CARDINALITY_REQUIRED) {
                    throw new IllegalArgumentException("Invalid cardinality: " + cardinality);
                }
                mCardinality = cardinality;
                return this;
            }

            public Builder setIndexingType(int indexingType) {
                if (indexingType < INDEXING_TYPE_NONE || indexingType > INDEXING_TYPE_RANGE) {
                    throw new IllegalArgumentException("Invalid indexingType: " + indexingType);
                }
                mIndexingType = indexingType;
                return this;
            }

            public LongPropertyConfig build() {
                return new LongPropertyConfig(mPropertyName, mCardinality, mIndexingType);
            }
        }
    }

    public static final class DoublePropertyConfig extends PropertyConfig {
        DoublePropertyConfig(String name, int cardinality) {
            super(name, TYPE_DOUBLE, cardinality);
        }

        @Override
        void writeToParcel(Parcel dest) {
            dest.writeInt(TYPE_DOUBLE);
            dest.writeString(mName);
            dest.writeInt(mCardinality);
        }

        public static final class Builder {
            private final String mPropertyName;
            private int mCardinality = CARDINALITY_OPTIONAL;

            public Builder(String propertyName) {
                mPropertyName = Objects.requireNonNull(propertyName);
            }

            public Builder setCardinality(int cardinality) {
                if (cardinality < CARDINALITY_REPEATED || cardinality > CARDINALITY_REQUIRED) {
                    throw new IllegalArgumentException("Invalid cardinality: " + cardinality);
                }
                mCardinality = cardinality;
                return this;
            }

            public DoublePropertyConfig build() {
                return new DoublePropertyConfig(mPropertyName, mCardinality);
            }
        }
    }

    public static final class BooleanPropertyConfig extends PropertyConfig {
        BooleanPropertyConfig(String name, int cardinality) {
            super(name, TYPE_BOOLEAN, cardinality);
        }

        @Override
        void writeToParcel(Parcel dest) {
            dest.writeInt(TYPE_BOOLEAN);
            dest.writeString(mName);
            dest.writeInt(mCardinality);
        }

        public static final class Builder {
            private final String mPropertyName;
            private int mCardinality = CARDINALITY_OPTIONAL;

            public Builder(String propertyName) {
                mPropertyName = Objects.requireNonNull(propertyName);
            }

            public Builder setCardinality(int cardinality) {
                if (cardinality < CARDINALITY_REPEATED || cardinality > CARDINALITY_REQUIRED) {
                    throw new IllegalArgumentException("Invalid cardinality: " + cardinality);
                }
                mCardinality = cardinality;
                return this;
            }

            public BooleanPropertyConfig build() {
                return new BooleanPropertyConfig(mPropertyName, mCardinality);
            }
        }
    }

    public static final class BytesPropertyConfig extends PropertyConfig {
        BytesPropertyConfig(String name, int cardinality) {
            super(name, TYPE_BYTES, cardinality);
        }

        @Override
        void writeToParcel(Parcel dest) {
            dest.writeInt(TYPE_BYTES);
            dest.writeString(mName);
            dest.writeInt(mCardinality);
        }

        public static final class Builder {
            private final String mPropertyName;
            private int mCardinality = CARDINALITY_OPTIONAL;

            public Builder(String propertyName) {
                mPropertyName = Objects.requireNonNull(propertyName);
            }

            public Builder setCardinality(int cardinality) {
                if (cardinality < CARDINALITY_REPEATED || cardinality > CARDINALITY_REQUIRED) {
                    throw new IllegalArgumentException("Invalid cardinality: " + cardinality);
                }
                mCardinality = cardinality;
                return this;
            }

            public BytesPropertyConfig build() {
                return new BytesPropertyConfig(mPropertyName, mCardinality);
            }
        }
    }

    public static final class DocumentPropertyConfig extends PropertyConfig {
        private final String mSchemaType;
        private final boolean mShouldIndexNestedProperties;
        private final List<String> mIndexableNestedPropertiesList;

        DocumentPropertyConfig(
                String name,
                int cardinality,
                String schemaType,
                boolean shouldIndexNestedProperties,
                List<String> indexableNestedProperties) {
            super(name, TYPE_DOCUMENT, cardinality);
            mSchemaType = Objects.requireNonNull(schemaType);
            mShouldIndexNestedProperties = shouldIndexNestedProperties;
            mIndexableNestedPropertiesList = Collections.unmodifiableList(
                    new ArrayList<String>(indexableNestedProperties));
        }

        public String getSchemaType() {
            return mSchemaType;
        }

        public boolean shouldIndexNestedProperties() {
            return mShouldIndexNestedProperties;
        }

        public List<String> getIndexableNestedProperties() {
            return mIndexableNestedPropertiesList;
        }

        @Override
        void writeToParcel(Parcel dest) {
            dest.writeInt(TYPE_DOCUMENT);
            dest.writeString(mName);
            dest.writeInt(mCardinality);
            dest.writeString(mSchemaType);
            dest.writeInt(mShouldIndexNestedProperties ? 1 : 0);
            dest.writeInt(mIndexableNestedPropertiesList.size());
            for (String p : mIndexableNestedPropertiesList) {
                dest.writeString(p);
            }
        }

        @Override
        public boolean equals(Object other) {
            if (!super.equals(other)) return false;
            DocumentPropertyConfig that = (DocumentPropertyConfig) other;
            return mShouldIndexNestedProperties == that.mShouldIndexNestedProperties
                    && Objects.equals(mSchemaType, that.mSchemaType)
                    && Objects.equals(
                            mIndexableNestedPropertiesList, that.mIndexableNestedPropertiesList);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    Integer.valueOf(super.hashCode()),
                    mSchemaType,
                    Boolean.valueOf(mShouldIndexNestedProperties),
                    mIndexableNestedPropertiesList);
        }

        public static final class Builder {
            private final String mPropertyName;
            private final String mSchemaType;
            private int mCardinality = CARDINALITY_OPTIONAL;
            private boolean mShouldIndexNestedProperties = false;
            private final Set<String> mIndexableNestedPropertiesList = new LinkedHashSet<String>();

            public Builder(String propertyName, String schemaType) {
                mPropertyName = Objects.requireNonNull(propertyName);
                mSchemaType = Objects.requireNonNull(schemaType);
            }

            public Builder setCardinality(int cardinality) {
                if (cardinality < CARDINALITY_REPEATED || cardinality > CARDINALITY_REQUIRED) {
                    throw new IllegalArgumentException("Invalid cardinality: " + cardinality);
                }
                mCardinality = cardinality;
                return this;
            }

            public Builder setShouldIndexNestedProperties(boolean indexNestedProperties) {
                mShouldIndexNestedProperties = indexNestedProperties;
                return this;
            }

            public Builder addIndexableNestedProperties(String... indexableNestedProperties) {
                Objects.requireNonNull(indexableNestedProperties);
                return addIndexableNestedProperties(Arrays.asList(indexableNestedProperties));
            }

            public Builder addIndexableNestedPropertyPaths(
                    PropertyPath... indexableNestedPropertyPaths) {
                Objects.requireNonNull(indexableNestedPropertyPaths);
                return addIndexableNestedPropertyPaths(
                        Arrays.asList(indexableNestedPropertyPaths));
            }

            public Builder addIndexableNestedProperties(
                    Collection<String> indexableNestedProperties) {
                Objects.requireNonNull(indexableNestedProperties);
                for (String p : indexableNestedProperties) {
                    Objects.requireNonNull(p);
                    mIndexableNestedPropertiesList.add(p);
                }
                return this;
            }

            public Builder addIndexableNestedPropertyPaths(
                    Collection<PropertyPath> indexableNestedPropertyPaths) {
                Objects.requireNonNull(indexableNestedPropertyPaths);
                for (PropertyPath pp : indexableNestedPropertyPaths) {
                    Objects.requireNonNull(pp);
                    mIndexableNestedPropertiesList.add(pp.toString());
                }
                return this;
            }

            public DocumentPropertyConfig build() {
                if (mShouldIndexNestedProperties && !mIndexableNestedPropertiesList.isEmpty()) {
                    throw new IllegalArgumentException(
                            "DocumentIndexingConfig#shouldIndexNestedProperties is required to be false when one or more indexableNestedProperties are provided.");
                }
                return new DocumentPropertyConfig(
                        mPropertyName,
                        mCardinality,
                        mSchemaType,
                        mShouldIndexNestedProperties,
                        new ArrayList<String>(mIndexableNestedPropertiesList));
            }
        }
    }
}
