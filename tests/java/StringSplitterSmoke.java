// SPDX-License-Identifier: GPL-3.0-only
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Arrays;
public class StringSplitterSmoke {
    public static void main(String[] args) {
        TextUtils.StringSplitter splitter = new TextUtils.SimpleStringSplitter(',');
        check(splitter, "", new String[]{});
        check(splitter, ",a,,b,", new String[]{"", "a", "", "b"});
        check(splitter, "reset", new String[]{"reset"});
        try { splitter.iterator().remove(); throw new AssertionError("remove"); }
        catch (UnsupportedOperationException expected) {}
        System.out.println("STRING_SPLITTER_OK");
    }
    private static void check(TextUtils.StringSplitter splitter, String input, String[] expected) {
        splitter.setString(input);
        ArrayList<String> actual = new ArrayList<>();
        for (String part : splitter) actual.add(part);
        if (!actual.equals(Arrays.asList(expected))) throw new AssertionError(actual);
    }
}
