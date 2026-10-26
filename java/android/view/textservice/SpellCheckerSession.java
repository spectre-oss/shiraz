// SPDX-License-Identifier: GPL-3.0-only
package android.view.textservice;

/** Spell checking is unavailable when the host has no spelling service. */
public final class SpellCheckerSession {
    private SpellCheckerSession() { }
    public interface SpellCheckerSessionListener {
        void onGetSuggestions(SuggestionsInfo[] results);
        void onGetSentenceSuggestions(SentenceSuggestionsInfo[] results);
    }
}
