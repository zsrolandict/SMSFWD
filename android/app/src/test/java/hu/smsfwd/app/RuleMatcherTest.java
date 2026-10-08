package hu.smsfwd.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class RuleMatcherTest {
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
