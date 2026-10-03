package com.takumistudios.notifymod.client.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.DoubleUnaryOperator;
import org.junit.jupiter.api.Test;

/** Fase 2 (§8): easings, compilación de presentaciones, estado de las pistas y reloj con pausa. */
class TimelineTest {
    @Test
    void todasLasCurvasEmpiezanEn0YTerminanEn1() {
        assertTrue(Easing.all().size() >= 34);
        for (String name : Easing.all().keySet()) {
            DoubleUnaryOperator e = Easing.parse(name);
            assertEquals(0, e.applyAsDouble(0), 1e-9, name);
            assertEquals(1, e.applyAsDouble(1), 1e-9, name);
            assertTrue(Double.isFinite(e.applyAsDouble(0.37)), name);
        }
        // Formas conocidas
        assertEquals(0.5, Easing.parse("linear").applyAsDouble(0.5), 1e-9);
        assertEquals(0.25, Easing.parse("ease_in_quad").applyAsDouble(0.5), 1e-9);
        assertEquals(0.5, Easing.parse("ease_in_out_cubic").applyAsDouble(0.5), 1e-9);
        assertTrue(Easing.parse("ease_out_back").applyAsDouble(0.7) > 1, "back se pasa de 1");
        assertTrue(Easing.parse("ease_out_expo").applyAsDouble(0.3) > 0.8, "expo frena rápido");
        assertEquals(0.5, Easing.parse("cubic_bezier(0,0,1,1)").applyAsDouble(0.5), 1e-6);
        assertThrows(IllegalArgumentException.class, () -> Easing.parse("ease_out_spaghetti"));
        assertThrows(IllegalArgumentException.class, () -> Easing.parse("cubic_bezier(1,2)"));
    }

    private static final String PLAN_EXAMPLE = """
            {
              "format_version": 1,
              "_comment": "Ejemplo del plan",
              "id": "notifymod_example:cinematic_purga",
              "canvas": "16:9",
              "background": { "type": "dim", "color": "#220000", "opacity": 0.8 },
              "duration": 7000,
              "tracks": [
                { "type": "sound", "time": 0, "sound": "notifymod_example:siren_alarm" },
                {
                  "type": "texture", "time": 500,
                  "file": "notifymod_example:textures/skull.gif",
                  "anchor": "center", "size": [0.25, 0.25],
                  "animation_in": { "type": "pop", "easing": "ease_out_elastic", "duration": 600 }
                },
                {
                  "type": "text", "time": 1500,
                  "content": { "translate": "notifymod_example.purga.title" },
                  "font": "minecraft:illageralt", "color": "#FF0000",
                  "anchor": "center", "offset": [0, 0.2],
                  "animation_in": { "type": "slide_right", "easing": "ease_out_expo", "duration": 500 }
                }
              ],
              "docked": { "omit": ["background"] }
            }""";

    @Test
    void compilaElEjemploDelPlan() {
        Presentation p = PresentationParser.parse("notifymod_example:cinematic_purga", PLAN_EXAMPLE);
        assertEquals(7000, p.durationMs());
        assertEquals(0x220000, p.background().color());
        assertEquals(0.8f, p.background().opacity(), 1e-6);
        assertEquals(3, p.tracks().size());
        TrackSpec text = p.tracks().get(2);
        assertEquals(TrackSpec.Type.TEXT, text.type());
        assertTrue(text.content().translate());
        assertEquals(0xFF0000, text.color());
        assertEquals(0.2f, text.offsetY(), 1e-6);
        // La fuente todavía no se aplica: se avisa, no falla
        assertTrue(p.warnings().stream().anyMatch(w -> w.contains("font")), p.warnings().toString());
    }

    @Test
    void rechazaPresentacionesNoValidas() {
        assertThrows(IllegalArgumentException.class, () -> PresentationParser.parse("a:b", "[]"));
        assertThrows(IllegalArgumentException.class, () -> PresentationParser.parse("a:b", "{\"duration\": 999999}"));
        assertThrows(IllegalArgumentException.class, () -> PresentationParser.parse("a:b",
                "{\"tracks\": [{\"type\": \"video\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> PresentationParser.parse("a:b",
                "{\"tracks\": [{\"type\": \"texture\", \"file\": \"a:../../x.png\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> PresentationParser.parse("a:b",
                "{\"tracks\": [{\"type\": \"text\", \"color\": \"rojo\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> PresentationParser.parse("a:b",
                "{\"tracks\": [{\"type\": \"text\", \"loop\": {\"type\": \"fade\"}}]}"));
        StringBuilder many = new StringBuilder("{\"tracks\": [");
        for (int i = 0; i <= PresentationParser.MAX_TRACKS; i++) {
            many.append(i == 0 ? "" : ",").append("{\"type\": \"shape\"}");
        }
        assertThrows(IllegalArgumentException.class, () -> PresentationParser.parse("a:b", many.append("]}").toString()));
    }

    @Test
    void ordenaPorCapaYUsaElMensaje() {
        Presentation p = PresentationParser.parse("a:b", """
                {"tracks": [
                  {"type": "text", "z": 5},
                  {"type": "shape", "z": -1, "duration": "2s"}
                ]}""");
        assertEquals(TrackSpec.Type.SHAPE, p.tracks().get(0).type());
        assertEquals(2000, p.tracks().get(0).durationMs());
        assertTrue(p.tracks().get(1).useMessage());
        assertNull(p.tracks().get(1).content());
    }

    @Test
    void estadoDeLasPistas() {
        Presentation p = PresentationParser.parse("a:b", """
                {"duration": 3000, "tracks": [
                  {"type": "shape", "time": 1000,
                   "animation_in": {"type": "fade", "duration": 200},
                   "animation_out": {"type": "slide_right", "duration": 500, "amount": 0.2}},
                  {"type": "text", "animation_in": {"type": "typewriter", "duration": 1000}}
                ]}""");
        TrackState s = new TrackState();
        TrackSpec shape = p.tracks().get(0);
        assertFalse(s.evaluate(shape, 999, p.durationMs()).visible);
        assertEquals(0.5f, s.evaluate(shape, 1100, p.durationMs()).alpha, 1e-4);
        assertEquals(1f, s.evaluate(shape, 2000, p.durationMs()).alpha, 1e-4);
        // A mitad de la salida: medio desplazado hacia la izquierda (slide_right sale... al revés de como entra)
        TrackState leaving = s.evaluate(shape, 2750, p.durationMs());
        assertEquals(0.5f, leaving.alpha, 1e-4);
        assertEquals(-0.1f, leaving.dx, 1e-4);
        assertFalse(s.evaluate(shape, 3000, p.durationMs()).visible);
        assertEquals(0.5f, s.evaluate(p.tracks().get(1), 500, p.durationMs()).reveal, 1e-4);
    }

    @Test
    void unCampoConOtroTipoDaUnErrorClaro() {
        // Antes se escapaba un ClassCastException de Gson en vez de IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> PresentationParser.parse("a:b",
                "{\"duration\": 1000, \"background\": \"dim\"}"));
        assertThrows(IllegalArgumentException.class, () -> PresentationParser.parse("a:b",
                "{\"duration\": 1000, \"tracks\": [{\"type\": \"text\", \"time\": 0, \"offset\": 3}]}"));
        assertThrows(IllegalArgumentException.class, () -> PresentationParser.parse("a:b",
                "{\"duration\": 1000, \"tracks\": [{\"type\": \"text\", \"time\": 0, \"loop\": [1]}]}"));
    }

    @Test
    void animacionEnFormaCorta() {
        Presentation p = PresentationParser.parse("a:b",
                "{\"duration\": 1000, \"tracks\": [{\"type\": \"text\", \"time\": 0, \"loop\": \"pulse\"}]}");
        assertEquals(AnimSpec.Type.parse("pulse"), p.tracks().getFirst().loop().type());
        assertEquals(1000, p.tracks().getFirst().loop().durationMs());
    }

    @Test
    void elRelojSeParaConLaPausa() {
        PausableClock clock = new PausableClock();
        clock.update(1000, false);
        assertEquals(1000, clock.now());
        clock.update(1500, true);
        clock.update(4000, true);
        assertEquals(1500, clock.now());
        clock.update(4100, false);
        assertEquals(1500, clock.now(), "se reanuda donde se paró");
        clock.update(4200, false);
        assertEquals(1600, clock.now());
    }
}


