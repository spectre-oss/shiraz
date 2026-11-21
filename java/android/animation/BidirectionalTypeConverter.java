// SPDX-License-Identifier: GPL-3.0-only
package android.animation;

/** Two-way {@link TypeConverter} supporting both forward and inverse property conversions. */
public abstract class BidirectionalTypeConverter<T, V> extends TypeConverter<T, V> {
    private BidirectionalTypeConverter<V, T> invertedConverter;

    public BidirectionalTypeConverter(Class<T> fromClass, Class<V> toClass) {
        super(fromClass, toClass);
    }

    public abstract T convertBack(V value);

    public BidirectionalTypeConverter<V, T> invert() {
        if (invertedConverter == null) {
            invertedConverter = new InvertedConverter<V, T>(this);
        }
        return invertedConverter;
    }

    private static final class InvertedConverter<From, To>
            extends BidirectionalTypeConverter<From, To> {
        private final BidirectionalTypeConverter<To, From> converter;

        InvertedConverter(BidirectionalTypeConverter<To, From> converter) {
            super(converter.getTargetType(), converter.getSourceType());
            this.converter = converter;
        }

        @Override
        public To convert(From value) {
            return converter.convertBack(value);
        }

        @Override
        public From convertBack(To value) {
            return converter.convert(value);
        }

        @Override
        public BidirectionalTypeConverter<To, From> invert() {
            return converter;
        }
    }
}
