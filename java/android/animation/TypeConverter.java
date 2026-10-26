// SPDX-License-Identifier: GPL-3.0-only
package android.animation;
/** Converts an animation sample to the type accepted by its target property. */
public abstract class TypeConverter<T,V> {
 private final Class<T> source;
 private final Class<V> target;
 public TypeConverter(Class<T> fromClass,Class<V> toClass){if(fromClass==null||toClass==null)throw new NullPointerException("converter type");source=fromClass;target=toClass;}
 Class<T> getSourceType(){return source;}
 Class<V> getTargetType(){return target;}
 public abstract V convert(T value);
}
