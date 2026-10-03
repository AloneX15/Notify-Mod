package com.takumistudios.notifymod.server;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TriggerIdArgumentTest {
    @Test void acceptsNamespacedAndNestedIdsAndStopsAtWhitespace() throws Exception {
        StringReader reader = new StringReader("example:nested/death cooldown 10s");
        assertEquals("example:nested/death", new TriggerIdArgument().parse(reader));
        assertEquals(" cooldown 10s", reader.getRemaining());
        assertEquals("notifymod:player_death", new TriggerIdArgument().parse(new StringReader("player_death")));
    }
    @Test void rejectsInvalidIdsWithSyntaxError() {
        assertThrows(CommandSyntaxException.class, () -> new TriggerIdArgument().parse(new StringReader("../bad")));
    }
}
