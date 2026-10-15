package com.recsys;

import com.recsys.exception.ValidationException;
import com.recsys.model.RecOutcome;
import com.recsys.util.PasswordHasher;
import com.recsys.util.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtilTest {

    @Test
    void passwordHashVerifiesAndIsSalted() {
        String h1 = PasswordHasher.hash("Secret123");
        String h2 = PasswordHasher.hash("Secret123");
        assertNotEquals(h1, h2, "same password must produce different salted hashes");
        assertTrue(PasswordHasher.verify("Secret123", h1));
        assertFalse(PasswordHasher.verify("secret123", h1));
        assertFalse(PasswordHasher.verify("Secret123", "garbage"));
    }

    @Test
    void emailIsNormalisedAndValidated() {
        assertEquals("a@b.com", Validator.email("  A@B.com "));
        assertThrows(ValidationException.class, () -> Validator.email("not-an-email"));
    }

    @Test
    void passwordRulesAreEnforced() {
        assertThrows(ValidationException.class, () -> Validator.password("short1"));
        assertThrows(ValidationException.class, () -> Validator.password("onlyletters"));
        assertEquals("Passw0rd!", Validator.password("Passw0rd!"));
    }

    @Test
    void tagRulesAreEnforced() {
        assertEquals("sci-fi", Validator.tag(" Sci-Fi "));
        assertThrows(ValidationException.class, () -> Validator.tag("<script>"));
    }

    @Test
    void numericRangeRejectsFractionsAndOutOfRange() {
        assertThrows(ValidationException.class, () -> Validator.intInRange(2.5, 0, 5, "x"));
        assertThrows(ValidationException.class, () -> Validator.intInRange(6, 0, 5, "x"));
        assertEquals(3, Validator.intInRange(3, 0, 5, "x"));
    }

    @Test
    void outcomesOnlyImprove() {
        assertTrue(RecOutcome.CLICK.supersedes(RecOutcome.SHOWN));
        assertTrue(RecOutcome.PURCHASE.supersedes(RecOutcome.LIKE));
        assertFalse(RecOutcome.CLICK.supersedes(RecOutcome.LIKE));
        assertTrue(RecOutcome.DISMISSED.supersedes(RecOutcome.SHOWN));
        assertFalse(RecOutcome.DISMISSED.supersedes(RecOutcome.CLICK));
    }
}
