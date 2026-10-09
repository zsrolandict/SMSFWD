package hu.smsfwd.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class RuleMatcherTest {
    @Test public void unicodeSpacesAndLiteralPunctuationMatch() {
        assertTrue(RuleMatcher.matchesKeywords("egyszer használatos", "any", "egyszer\u00a0\u202fhasználatos"));
        assertTrue(RuleMatcher.matchesKeywords("\u00a0InfoCert\u00a0", "any", "INFOCERT"));
        assertTrue(RuleMatcher.matchesKeywords("OTP", "any", "OTP\u0085érkezett"));
        assertTrue(RuleMatcher.matchesKeywords("kód (új)", "any", "A kód (új): 1234"));
        assertFalse(RuleMatcher.matchesKeywords("kód (új)", "any", "A kód új: 1234"));
        assertTrue(RuleMatcher.matchesKeywords("", "any", "Tetszőleges SMS"));
        assertTrue(RuleMatcher.matchesKeywords("\u00a0\n\t", "all", "Tetszőleges SMS"));
    }
    @Test public void screenshotPatternsMatchAndOtherMessagesDoNot() {
        String terms="egyszer használatos jelszava\nInfoCert";
        assertTrue(RuleMatcher.matchesKeywords(terms,"any","Az Ön egyszer használatos jelszava: 00000000"));
        assertTrue(RuleMatcher.matchesKeywords(terms,"any","OTP: 00000000 Codice di verifica InfoCert generato alle ore: 10:00:00"));
        assertFalse(RuleMatcher.matchesKeywords(terms,"any","Szia, találkozunk holnap?"));
    }
    @Test public void wordsAndAllConditionsWork() {
        assertFalse(RuleMatcher.matchesKeywords("OTP","any","NOTP"));
        assertTrue(RuleMatcher.matchesKeywords("OTP","any","OTP: 00000000"));
        assertFalse(RuleMatcher.matchesKeywords("OTP\nInfoCert","all","OTP: 00000000"));
        assertTrue(RuleMatcher.matchesKeywords("OTP\nInfoCert","all","otp: 00000000 INFOCERT"));
        assertTrue(RuleMatcher.matchesKeywords("egyszer használatos","any","egyszer\n használatos"));
    }
    @Test public void internationalNumbersNormalize() { assertEquals("+36301234567", RuleMatcher.normalize("0036 (30) 123-4567")); }
    @Test public void senderAndKeywordAreBothRequired() {
        assertTrue(RuleMatcher.matches("specific", "+36 30 1234567", "munka", "0036301234567", "MUNKA megérkezett"));
        assertFalse(RuleMatcher.matches("specific", "+36301234567", "munka", "+36309999999", "munka"));
        assertFalse(RuleMatcher.matches("specific", "+36301234567", "munka", "+36301234567", "otthon"));
    }
    @Test public void namedSenderMatchesWithoutNumericGuessing() {
        assertTrue(RuleMatcher.matches("specific", "BANK", "", "Bank", "üzenet"));
        assertFalse(RuleMatcher.matches("specific", "1234567", "", "+36301234567", "üzenet"));
    }
    @Test public void accentsArePreserved() {
        assertTrue(RuleMatcher.matches("any", "", "érkezett", "Bank", "ÉRKEZETT"));
        assertFalse(RuleMatcher.matches("any", "", "érkezett", "Bank", "erkezett"));
    }
}
