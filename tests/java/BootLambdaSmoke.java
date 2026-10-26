// SPDX-License-Identifier: GPL-3.0-only
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import java.util.stream.*;

/** Anonymous application callbacks isolate invoke-custom calls in the boot library. */
public final class BootLambdaSmoke {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static final class Length implements ToLongFunction<String>, Serializable {
        public long applyAsLong(String value) { return value.length(); }
    }
    public static void main(String[] args) {
        Comparator<String> length = Comparator.comparingLong(new Length());
        check(length instanceof Serializable, "serializable comparator marker");
        check(length.compare("abcd", "x") > 0 && length.compare("a", "b") == 0, "captured long extractor");
        check(length.reversed().compare("abcd", "x") < 0, "reversed default method");
        Comparator<String> combined = length.thenComparing(Comparator.<String>naturalOrder());
        List<String> values = new ArrayList<String>(Arrays.asList("zz", "aa", "b", "abc"));
        values.sort(combined);
        check(values.equals(Arrays.asList("b", "aa", "zz", "abc")), "chained sort");
        check(Comparator.nullsFirst(combined).compare(null, "x") < 0, "null comparator");
        check(Comparator.comparingInt(new ToIntFunction<String>() {
            public int applyAsInt(String value) { return value.length(); }
        }).compare("abc", "z") > 0, "int extractor");
        check(Comparator.comparingDouble(new ToDoubleFunction<Double>() {
            public double applyAsDouble(Double value) { return value; }
        }).compare(Double.NaN, Double.POSITIVE_INFINITY) > 0, "double ordering");
        try { Comparator.comparingLong(null); throw new AssertionError("null extractor"); }
        catch (NullPointerException expected) { }
        Function<String,Integer> parse = new Function<String,Integer>() {
            public Integer apply(String value) { return Integer.valueOf(value); }
        };
        Function<String,Integer> multiplied = parse.andThen(new Function<Integer,Integer>() {
            public Integer apply(Integer value) { return value * 3; }
        });
        check(multiplied.apply("7") == 21, "captured function composition");
        check(Function.<String>identity().apply("same").equals("same"), "identity factory");
        final AtomicInteger calls = new AtomicInteger();
        Predicate<String> yes = new Predicate<String>() {
            public boolean test(String value) { calls.incrementAndGet(); return true; }
        };
        Predicate<String> no = new Predicate<String>() {
            public boolean test(String value) { calls.incrementAndGet(); return false; }
        };
        check(!no.and(yes).test("x") && calls.get() == 1, "predicate and short circuit");
        check(yes.or(no).test("x") && calls.get() == 2, "predicate or short circuit");
        check(!yes.negate().test("x") && calls.get() == 3, "predicate negate");
        try {
            multiplied.apply("invalid");
            throw new AssertionError("callback exception");
        } catch (NumberFormatException expected) { }
        List<Integer> mapped = Arrays.asList("3", "1", "2").stream().map(parse).sorted().collect(Collectors.<Integer>toList());
        check(mapped.equals(Arrays.asList(1, 2, 3)), "stream capture, collect and ordering");
        check(IntStream.range(0, 10).sum() == 45, "primitive stream reduction");
        check(LongStream.of(Long.MIN_VALUE, 0, Long.MAX_VALUE).max().getAsLong() == Long.MAX_VALUE, "long primitive capture");
        check(DoubleStream.of(1.0, 2.0, 3.0).average().getAsDouble() == 2.0, "double stream reduction");
        Map<String,Integer> map = new HashMap<String,Integer>();
        map.merge("x", 2, new BiFunction<Integer,Integer,Integer>() { public Integer apply(Integer a, Integer b) { return a + b; } });
        map.merge("x", 3, new BiFunction<Integer,Integer,Integer>() { public Integer apply(Integer a, Integer b) { return a + b; } });
        check(map.get("x") == 5, "map merger callback");
        System.out.println("BOOT_LAMBDA_COMPARATORS_FUNCTIONS_STREAMS_OK");
    }
}
