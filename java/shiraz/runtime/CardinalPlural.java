// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import java.util.Locale;
/** Integer cardinal categories used by resource quantity strings. */
public final class CardinalPlural {
 private CardinalPlural() {}
 public static String select(Locale locale, int quantity) {
  int n = quantity < 0 ? -quantity : quantity;
  String language = locale == null ? "en" : locale.getLanguage();
  if (language.equals("ar")) return arabic(n);
  if (language.equals("fr") || language.equals("ff") || language.equals("hy") || language.equals("kab")) return n == 0 || n == 1 ? "one" : "other";
  if (language.equals("cs") || language.equals("sk")) return n == 1 ? "one" : n >= 2 && n <= 4 ? "few" : "other";
  if (language.equals("pl")) return polish(n);
  if (language.equals("ru") || language.equals("uk") || language.equals("be") || language.equals("sr") || language.equals("hr") || language.equals("bs")) return slavic(n);
  return n == 1 ? "one" : "other";
 }
 private static String slavic(int n) {
  int mod10 = n % 10, mod100 = n % 100;
  if (mod10 == 1 && mod100 != 11) return "one";
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return "few";
  return "many";
 }
 private static String polish(int n) {
  int mod10 = n % 10, mod100 = n % 100;
  if (n == 1) return "one";
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return "few";
  return "many";
 }
 private static String arabic(int n) {
  if (n == 0) return "zero";
  if (n == 1) return "one";
  if (n == 2) return "two";
  int mod100 = n % 100;
  if (mod100 >= 3 && mod100 <= 10) return "few";
  if (mod100 >= 11 && mod100 <= 99) return "many";
  return "other";
 }
}
