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
    static boolean matchesKeywords(String terms,String mode,String body) {
        String normalized=java.text.Normalizer.normalize(body,java.text.Normalizer.Form.NFC).toLowerCase(Locale.forLanguageTag("hu")).replaceAll("(?U)\\s+"," ");
        boolean all=mode.equals("all"),hasTerms=false;
        for(String raw:terms.split("\n")) {
            String term=java.text.Normalizer.normalize(raw.trim(),java.text.Normalizer.Form.NFC).toLowerCase(Locale.forLanguageTag("hu")).replaceAll("(?U)\\s+"," ");
            if(term.isEmpty())continue;hasTerms=true;
            boolean found=java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}_])"+java.util.regex.Pattern.quote(term)+"(?![\\p{L}\\p{N}_])").matcher(normalized).find();
            if(all && !found)return false;if(!all && found)return true;
        }
        return !hasTerms || all;
    }
}
