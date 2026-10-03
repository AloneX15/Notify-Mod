package com.takumistudios.notifymod.core;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class NotificationStyleTest {
    @Test void defaultsAndRoundTrip() {
        var style = NotificationStyle.DEFAULTS;
        assertEquals(style, NotificationStyle.parse(style.toJson()));
        var n = Template.parse("test:notice", "{\"channels\":\"hud\",\"message\":\"Hello\"}")
                .toNotification(null, null, null, null);
        assertEquals(Placement.TOP_RIGHT, n.placement());
        assertEquals(style, n.style());
    }
    @Test void customTitleAndThemeSurviveOverrides() {
        var t = Template.parse("test:notice", """
                {"channels":["hud","chat"],"message":"Hello {name}",
                 "style":{"title":"Mission","chat_color":"#00FF00","title_color":"#FF2200",
                 "text_color":"#FFAABB","background_opacity":0.4,"width":320,"show_icon":false}}
                """);
        var n = t.toNotification(NotificationArgs.EMPTY, Placement.BOTTOM_RIGHT, Priority.HIGH, true);
        assertEquals("Mission", n.style().title().text());
        assertEquals(0xFF2200, n.style().titleColor());
        assertEquals(320, n.style().width());
        assertFalse(n.style().showIcon());
        assertEquals(n.style(), NotificationStyle.parse(n.style().toJson()));
    }
    @Test void rejectsInvalidExternalStyle() {
        for (String json : java.util.List.of("{\"text_color\":\"red\"}", "{\"width\":240.5}",
                "{\"width\":999}", "{\"shadow\":\"false\"}", "{\"background_opacity\":2}",
                "{\"background_opacity\":\"NaN\"}", "{\"unknown\":true}", "{\"title\":123}", "{\"title\":{\"text\":true}}",
                "{\"title\":{\"translate\":\"key\",\"with\":[{}]}}",
                "{\"title\":\"" + "x".repeat(129) + "\"}")) {
            assertThrows(IllegalArgumentException.class, () -> NotificationStyle.parse(JsonParser.parseString(json).getAsJsonObject()));
        }
    }
}
