package com.takumistudios.notifymod.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TriggerTest {
    private static final String BASE = "{\"format_version\":1,\"type\":\"player_death\",\"template\":\"notifymod:death\"";
    private static Trigger parse(String extra) { return Trigger.parse("notifymod:death", BASE + extra + "}"); }

    @Test void disabledByDefault() {
        Trigger trigger = parse("");
        assertFalse(trigger.enabled());
        assertEquals("after_respawn", trigger.causeDelivery());
        assertEquals(6, trigger.maxPerMinute());
    }

    @Test void resolvesAndSanitizesEventValues() {
        Trigger trigger = parse(",\"args\":{\"jugador\":\"$player\",\"literal\":\"hello\"}");
        assertEquals("Alice", trigger.resolveArgs(Map.of("player", "A\nlice")).get("jugador"));
        assertEquals("hello", trigger.resolveArgs(Map.of()).get("literal"));
        assertEquals(256, trigger.resolveArgs(Map.of("player", "x".repeat(2000))).get("jugador").length());
    }

    @Test void coordinatesRequireExplicitConsent() {
        assertThrows(IllegalArgumentException.class, () -> parse(",\"args\":{\"pos\":\"$x\"}"));
        assertEquals("10", parse(",\"allow_coordinates\":true,\"args\":{\"pos\":\"$x\"}").resolveArgs(Map.of("x", "10")).get("pos"));
    }

    @Test void unsupportedFeaturesFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> parse(",\"coalesce\":{\"mode\":\"queue\"}"));
        assertThrows(IllegalArgumentException.class, () -> parse(",\"conditions\":{\"chance\":0.5}"));
        assertThrows(IllegalArgumentException.class, () -> parse(",\"args\":{\"prefix\":\"$prefix\"}"));
        assertThrows(IllegalArgumentException.class, () -> parse(",\"audience\":{\"include\":[{\"type\":\"selector\",\"value\":\"@a\"}]}"));
    }

    @Test void rejectsUnknownVersionAndOversizedPayload() {
        assertThrows(IllegalArgumentException.class, () -> Trigger.parse("notifymod:death", BASE.replace(":1", ":2") + "}"));
        assertThrows(IllegalArgumentException.class, () -> Trigger.parse("../evil", BASE + "}"));
        assertThrows(IllegalArgumentException.class, () -> Trigger.parse("notifymod:death", "x".repeat(65537)));
    }

    @Test void deadPlayersCannotReceiveImmediately() {
        assertThrows(IllegalArgumentException.class, () -> parse(",\"cause_delivery\":\"immediate\""));
    }

    @Test void supportsBothAudienceFormsAndExclusions() {
        Trigger trigger = parse(",\"audience\":{\"include\":[{\"group\":\"vip\"},{\"type\":\"all\"}],\"exclude\":[{\"permission\":\"notifymod:bypass\"}]} ");
        assertEquals(2, trigger.include().size());
        assertEquals("vip", trigger.include().getFirst().value());
        assertEquals("permission", trigger.exclude().getFirst().type());
        assertThrows(UnsupportedOperationException.class, () -> trigger.include().clear());
    }

    @Test void limitsAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> parse(",\"enabled\":\"true\""));
        assertThrows(IllegalArgumentException.class, () -> parse(",\"max_per_minute\":1.5"));
        assertThrows(IllegalArgumentException.class, () -> parse(",\"max_per_minute\":0"));
        assertThrows(IllegalArgumentException.class, () -> parse(",\"max_per_minute\":121"));
        assertThrows(IllegalArgumentException.class, () -> parse(",\"cooldown\":\"25h\""));
    }

    @Test void globalAndPlayerCooldownsDoNotExtendOnRejection() {
        Trigger trigger = parse(",\"cooldown\":\"1s\",\"per_player_cooldown\":\"2s\"");
        TriggerThrottle throttle = new TriggerThrottle();
        assertTrue(throttle.acquire(trigger, "a", 1000));
        assertFalse(throttle.acquire(trigger, "b", 1500));
        assertTrue(throttle.acquire(trigger, "b", 2000));
        assertFalse(throttle.acquire(trigger, "a", 2500));
        assertTrue(throttle.acquire(trigger, "a", 3000));
    }

    @Test void slidingMinuteWindowAndGlobalBurstAreBounded() {
        Trigger trigger = parse(",\"max_per_minute\":2");
        TriggerThrottle throttle = new TriggerThrottle();
        assertTrue(throttle.acquire(trigger, "a", 0));
        assertTrue(throttle.acquire(trigger, "a", 100));
        assertFalse(throttle.acquire(trigger, "a", 200));
        assertTrue(throttle.acquire(trigger, "a", 60_000));
        throttle.clear();
        Trigger burst = parse(",\"max_per_minute\":120");
        for (int i = 0; i < 20; i++) assertTrue(throttle.acquire(burst, "a", i));
        assertFalse(throttle.acquire(burst, "a", 100));
        assertTrue(throttle.acquire(burst, "a", 1000));
    }

    @Test void disconnectForgetsPlayerCooldownOnly() {
        Trigger trigger = parse(",\"per_player_cooldown\":\"1s\"");
        TriggerThrottle throttle = new TriggerThrottle();
        assertTrue(throttle.acquire(trigger, "a", 0));
        assertFalse(throttle.acquire(trigger, "a", 100));
        throttle.forget("a");
        assertTrue(throttle.acquire(trigger, "a", 200));
    }
}
