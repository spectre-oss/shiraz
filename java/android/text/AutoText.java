// SPDX-License-Identifier: GPL-3.0-only
package android.text;

import android.view.View;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Built-in English auto-text typo correction lookup table. */
public class AutoText {
    private static final Map<String, String> EN_AUTOTEXT = new HashMap<String, String>();

    static {
        EN_AUTOTEXT.put("abouta", "about a");
        EN_AUTOTEXT.put("aboutit", "about it");
        EN_AUTOTEXT.put("aboutthe", "about the");
        EN_AUTOTEXT.put("acn", "can");
        EN_AUTOTEXT.put("adn", "and");
        EN_AUTOTEXT.put("ahd", "had");
        EN_AUTOTEXT.put("ahve", "have");
        EN_AUTOTEXT.put("alot", "a lot");
        EN_AUTOTEXT.put("arent", "aren't");
        EN_AUTOTEXT.put("becuase", "because");
        EN_AUTOTEXT.put("cant", "can't");
        EN_AUTOTEXT.put("coudl", "could");
        EN_AUTOTEXT.put("couldnt", "couldn't");
        EN_AUTOTEXT.put("didnt", "didn't");
        EN_AUTOTEXT.put("doesnt", "doesn't");
        EN_AUTOTEXT.put("dont", "don't");
        EN_AUTOTEXT.put("hadnt", "hadn't");
        EN_AUTOTEXT.put("hasnt", "hasn't");
        EN_AUTOTEXT.put("havent", "haven't");
        EN_AUTOTEXT.put("hed", "he'd");
        EN_AUTOTEXT.put("hes", "he's");
        EN_AUTOTEXT.put("htat", "that");
        EN_AUTOTEXT.put("hte", "the");
        EN_AUTOTEXT.put("htere", "there");
        EN_AUTOTEXT.put("hvea", "have");
        EN_AUTOTEXT.put("id", "I'd");
        EN_AUTOTEXT.put("im", "I'm");
        EN_AUTOTEXT.put("isnt", "isn't");
        EN_AUTOTEXT.put("itll", "it'll");
        EN_AUTOTEXT.put("ive", "I've");
        EN_AUTOTEXT.put("jsut", "just");
        EN_AUTOTEXT.put("knwo", "know");
        EN_AUTOTEXT.put("nad", "and");
        EN_AUTOTEXT.put("nkow", "know");
        EN_AUTOTEXT.put("recieve", "receive");
        EN_AUTOTEXT.put("shoudl", "should");
        EN_AUTOTEXT.put("shouldnt", "shouldn't");
        EN_AUTOTEXT.put("taht", "that");
        EN_AUTOTEXT.put("teh", "the");
        EN_AUTOTEXT.put("thier", "their");
        EN_AUTOTEXT.put("wasnt", "wasn't");
        EN_AUTOTEXT.put("werent", "weren't");
        EN_AUTOTEXT.put("wiht", "with");
        EN_AUTOTEXT.put("wont", "won't");
        EN_AUTOTEXT.put("woudl", "would");
        EN_AUTOTEXT.put("wouldnt", "wouldn't");
        EN_AUTOTEXT.put("yuo", "you");
        EN_AUTOTEXT.put("yuor", "your");
    }

    private AutoText() {
    }

    private static boolean isEnglishLocale(View view) {
        Locale loc = null;
        if (view != null && view.getContext() != null && view.getContext().getResources() != null
                && view.getContext().getResources().getConfiguration() != null) {
            loc = view.getContext().getResources().getConfiguration().locale;
        }
        if (loc == null) {
            loc = Locale.getDefault();
        }
        return loc != null && "en".equalsIgnoreCase(loc.getLanguage());
    }

    public static String get(CharSequence src, int start, int end, View view) {
        if (src == null || start < 0 || end < start || end > src.length()) {
            return null;
        }
        if (!isEnglishLocale(view)) {
            return null;
        }
        String key = src.subSequence(start, end).toString();
        return EN_AUTOTEXT.get(key);
    }

    public static int getSize(View view) {
        return isEnglishLocale(view) ? EN_AUTOTEXT.size() : 0;
    }
}
