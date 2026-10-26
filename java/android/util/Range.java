// SPDX-License-Identifier: GPL-3.0-only
package android.util;
public final class Range<T extends Comparable<? super T>> {
 private final T lower,upper;
 public Range(T lower,T upper){if(lower==null||upper==null)throw new NullPointerException("range endpoint");if(lower.compareTo(upper)>0)throw new IllegalArgumentException("lower exceeds upper");this.lower=lower;this.upper=upper;}
 public static <T extends Comparable<? super T>> Range<T> create(T lower,T upper){return new Range<T>(lower,upper);}
 public T getLower(){return lower;}public T getUpper(){return upper;}
 public boolean contains(T value){return value.compareTo(lower)>=0&&value.compareTo(upper)<=0;}
 public boolean contains(Range<T> value){return contains(value.lower)&&contains(value.upper);}
 public T clamp(T value){return value.compareTo(lower)<0?lower:value.compareTo(upper)>0?upper:value;}
 public Range<T> intersect(Range<T> other){return intersect(other.lower,other.upper);}
 public Range<T> intersect(T lo,T hi){return new Range<T>(lower.compareTo(lo)>0?lower:lo,upper.compareTo(hi)<0?upper:hi);}
 public Range<T> extend(Range<T> other){return extend(other.lower,other.upper);}
 public Range<T> extend(T value){return extend(value,value);}
 public Range<T> extend(T lo,T hi){if(lo.compareTo(hi)>0)throw new IllegalArgumentException("lower exceeds upper");return new Range<T>(lower.compareTo(lo)<0?lower:lo,upper.compareTo(hi)>0?upper:hi);}
 @Override public boolean equals(Object value){if(!(value instanceof Range))return false;Range<?> r=(Range<?>)value;return lower.equals(r.lower)&&upper.equals(r.upper);}
 @Override public int hashCode(){return lower.hashCode()*31+upper.hashCode();}
 @Override public String toString(){return "["+lower+", "+upper+"]";}
}
