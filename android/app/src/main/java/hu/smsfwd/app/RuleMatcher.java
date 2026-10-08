package hu.smsfwd.app;
import java.util.Locale;
final class RuleMatcher {
    static String normalize(String value) {
        String s = value.trim().replaceAll("[\\s()\\-]", "");
        return s.startsWith("00") ? "+" + s.substring(2) : s;
    }
    static boolean matches(String senderType, String ruleSender, String keyword, String sender, String body) {
        if (senderType.equals("specific") && !normalize(ruleSender).equalsIgnoreCase(normalize(sender))) return false;
        return keyword.isEmpty() || body.toLowerCase(Locale.forLanguageTag("hu")).contains(keyword.toLowerCase(Locale.forLanguageTag("hu")));
    }
}
