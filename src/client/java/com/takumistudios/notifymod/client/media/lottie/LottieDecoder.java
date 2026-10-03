package com.takumistudios.notifymod.client.media.lottie;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.takumistudios.notifymod.client.media.FrameSequence;
import com.takumistudios.notifymod.client.media.MediaException;
import com.takumistudios.notifymod.client.media.MediaLimits;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Subconjunto de Lottie (Bodymovin) rasterizado con Java2D a una {@link FrameSequence}. Se pre-renderiza al cargar:
 * en la partida solo se suben fotogramas, igual que con un GIF.
 *
 * <p>Soportado: capas de formas (4), sólidas (1) y nulas (3) con parentesco; grupos, rectángulos, elipses y rutas;
 * rellenos y trazos de color; transformaciones con keyframes y easing de Bézier. Lo demás (imágenes, texto,
 * precomposiciones, máscaras, mates, degradados, recortes, expresiones…) se ignora y se anota en
 * {@link Result#unsupported()}.
 */
public final class LottieDecoder {
    private static final int MAX_NODES = 20_000;
    private static final int MAX_DEPTH = 32;

    private LottieDecoder() {
    }

    /** Animación decodificada y funciones del archivo que no se han podido representar. */
    public record Result(FrameSequence frames, List<String> unsupported) {
    }

    public static boolean matches(byte[] data) {
        for (byte b : data) {
            if (!Character.isWhitespace(b)) {
                return b == '{';
            }
        }
        return false;
    }

    public static Result decode(byte[] json, MediaLimits limits, int maxFps) throws MediaException {
        if (json.length > limits.maxFileBytes()) {
            throw new MediaException("Archivo Lottie demasiado grande");
        }
        try {
            JsonObject root = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
            LottieReport report = new LottieReport();
            Animation animation = new Parser(report).animation(root);
            return new Result(render(animation, limits, maxFps), report.list());
        } catch (MediaException e) {
            throw e;
        } catch (RuntimeException e) {
            // JsonParseException, IllegalStateException, ClassCastException, NumberFormatException…
            throw new MediaException("Lottie no válido: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- modelo

    private record Animation(double width, double height, double fps, double inPoint, double outPoint,
                             List<Layer> layers) {
    }

    private record Layer(int index, Integer parent, int type, double inPoint, double outPoint, double startTime,
                         double stretch, Transform transform, Group shapes, Color solidColor, double solidWidth,
                         double solidHeight) {
        double localFrame(double frame) {
            return (frame - startTime) / stretch;
        }
    }

    private record Transform(LottieProperty anchor, LottieProperty position, LottieProperty positionX,
                             LottieProperty positionY, LottieProperty scale, LottieProperty rotation,
                             LottieProperty opacity) {
        AffineTransform matrix(double frame) {
            double[] a = anchor.value(frame);
            double px, py;
            if (position != null) {
                double[] p = position.value(frame);
                px = at(p, 0);
                py = at(p, 1);
            } else {
                px = positionX.scalar(frame);
                py = positionY.scalar(frame);
            }
            double[] s = scale.value(frame);
            AffineTransform m = new AffineTransform();
            m.translate(px, py);
            m.rotate(Math.toRadians(rotation.scalar(frame)));
            m.scale(at(s, 0) / 100.0, s.length > 1 ? s[1] / 100.0 : at(s, 0) / 100.0);
            m.translate(-at(a, 0), -at(a, 1));
            return m;
        }

        double opacity(double frame) {
            return Math.clamp(opacity.scalar(frame) / 100.0, 0, 1);
        }
    }

    private sealed interface Item permits Group, Geometry, Fill, Stroke {
    }

    private record Group(List<Item> items, Transform transform) implements Item {
    }

    private record Geometry(Kind kind, LottieProperty position, LottieProperty size, LottieProperty roundness,
                            LottieProperty path, boolean closed) implements Item {
        enum Kind { RECT, ELLIPSE, PATH }

        Shape shape(double frame) {
            return switch (kind) {
                case RECT -> {
                    double[] p = position.value(frame), s = size.value(frame);
                    double r = roundness.scalar(frame);
                    yield new RoundRectangle2D.Double(at(p, 0) - at(s, 0) / 2, at(p, 1) - at(s, 1) / 2,
                            at(s, 0), at(s, 1), r * 2, r * 2);
                }
                case ELLIPSE -> {
                    double[] p = position.value(frame), s = size.value(frame);
                    yield new Ellipse2D.Double(at(p, 0) - at(s, 0) / 2, at(p, 1) - at(s, 1) / 2, at(s, 0), at(s, 1));
                }
                case PATH -> toPath2D(path.value(frame), closed);
            };
        }
    }

    private record Fill(LottieProperty color, LottieProperty opacity, boolean evenOdd) implements Item {
    }

    private record Stroke(LottieProperty color, LottieProperty opacity, LottieProperty width, int cap, int join,
                          float miter) implements Item {
    }

    // ---------------------------------------------------------------- lectura

    private static final class Parser {
        private final LottieReport report;
        private int nodes;

        Parser(LottieReport report) {
            this.report = report;
        }

        Animation animation(JsonObject root) throws MediaException {
            double w = num(root, "w", 0), h = num(root, "h", 0), fr = num(root, "fr", 30);
            double ip = num(root, "ip", 0), op = num(root, "op", 0);
            if (!(w > 0 && h > 0 && fr > 0 && op > ip) || !Double.isFinite(w + h + fr + ip + op)) {
                throw new MediaException("Cabecera Lottie no válida (w, h, fr, ip, op)");
            }
            if (root.has("assets") && !root.getAsJsonArray("assets").isEmpty()) {
                report.unsupported("recursos (assets: imágenes y precomposiciones)");
            }
            if (root.has("fonts") || root.has("chars")) {
                report.unsupported("fuentes y texto");
            }
            List<Layer> layers = new ArrayList<>();
            JsonArray array = root.getAsJsonArray("layers");
            if (array != null) {
                for (JsonElement e : array) {
                    Layer layer = layer(e.getAsJsonObject(), ip, op);
                    if (layer != null) {
                        layers.add(layer);
                    }
                }
            }
            return new Animation(w, h, fr, ip, op, layers);
        }

        private Layer layer(JsonObject json, double compIn, double compOut) throws MediaException {
            count();
            int type = (int) num(json, "ty", -1);
            if (json.has("hd") && json.get("hd").getAsBoolean()) {
                return null;
            }
            if (json.has("hasMask") && json.get("hasMask").getAsBoolean() || json.has("masksProperties")) {
                report.unsupported("máscaras");
            }
            if (json.has("tt")) {
                report.unsupported("mates (track mattes)");
            }
            if (json.has("ef")) {
                report.unsupported("efectos");
            }
            switch (type) {
                case 0 -> report.unsupported("precomposiciones");
                case 2 -> report.unsupported("capas de imagen");
                case 5 -> report.unsupported("capas de texto");
                case 1, 3, 4 -> { }
                default -> report.unsupported("capas de tipo " + type);
            }
            if (type != 1 && type != 3 && type != 4) {
                return null;
            }
            Integer parent = json.has("parent") ? (int) num(json, "parent", 0) : null;
            double sr = num(json, "sr", 1);
            Transform transform = transform(json.getAsJsonObject("ks"));
            Group shapes = type == 4 ? new Group(items(json.getAsJsonArray("shapes"), 0), null) : null;
            Color solid = type == 1 ? hexColor(json.has("sc") ? json.get("sc").getAsString() : "#000000") : null;
            return new Layer((int) num(json, "ind", -1), parent, type, num(json, "ip", compIn), num(json, "op", compOut),
                    num(json, "st", 0), sr == 0 ? 1 : sr, transform, shapes, solid, num(json, "sw", 0),
                    num(json, "sh", 0));
        }

        private List<Item> items(JsonArray array, int depth) throws MediaException {
            List<Item> items = new ArrayList<>();
            if (array == null) {
                return items;
            }
            if (depth > MAX_DEPTH) {
                throw new MediaException("Lottie con demasiados grupos anidados");
            }
            for (JsonElement e : array) {
                count();
                JsonObject json = e.getAsJsonObject();
                if (json.has("hd") && json.get("hd").getAsBoolean()) {
                    continue;
                }
                String ty = json.has("ty") ? json.get("ty").getAsString() : "";
                switch (ty) {
                    case "gr" -> {
                        List<Item> children = items(json.getAsJsonArray("it"), depth + 1);
                        Transform tr = null;
                        for (JsonElement child : json.getAsJsonArray("it")) {
                            JsonObject c = child.getAsJsonObject();
                            if (c.has("ty") && "tr".equals(c.get("ty").getAsString())) {
                                tr = transform(c);
                            }
                        }
                        items.add(new Group(children, tr));
                    }
                    case "rc" -> items.add(new Geometry(Geometry.Kind.RECT, prop(json, "p", 0, 0), prop(json, "s", 0, 0),
                            prop(json, "r", 0), null, true));
                    case "el" -> items.add(new Geometry(Geometry.Kind.ELLIPSE, prop(json, "p", 0, 0),
                            prop(json, "s", 0, 0), null, null, true));
                    case "sh" -> items.add(new Geometry(Geometry.Kind.PATH, null, null, null,
                            LottieProperty.parsePath(json.get("ks"), report, "sh"),
                            LottieProperty.pathClosed(json.get("ks"))));
                    case "fl" -> items.add(new Fill(prop(json, "c", 0, 0, 0, 1), prop(json, "o", 100),
                            json.has("r") && json.get("r").getAsInt() == 2));
                    case "st" -> items.add(new Stroke(prop(json, "c", 0, 0, 0, 1), prop(json, "o", 100),
                            prop(json, "w", 1), (int) num(json, "lc", 2), (int) num(json, "lj", 2),
                            (float) Math.max(1, num(json, "ml", 4))));
                    case "tr" -> { } // lo lee el grupo
                    case "gf", "gs" -> report.unsupported("degradados");
                    case "tm" -> report.unsupported("recorte de trazos (trim paths)");
                    case "rp" -> report.unsupported("repetidores");
                    case "sr" -> report.unsupported("estrellas y polígonos");
                    case "rd" -> report.unsupported("esquinas redondeadas");
                    case "mm" -> report.unsupported("fusión de trazados");
                    default -> report.unsupported("forma '" + ty + "'");
                }
            }
            return items;
        }

        private Transform transform(JsonObject json) {
            if (json == null) {
                json = new JsonObject();
            }
            LottieProperty position = null, px = null, py = null;
            JsonElement p = json.get("p");
            if (p != null && p.isJsonObject() && p.getAsJsonObject().has("s")
                    && p.getAsJsonObject().get("s").isJsonPrimitive() && p.getAsJsonObject().get("s").getAsBoolean()) {
                px = LottieProperty.parse(p.getAsJsonObject().get("x"), new double[] {0}, report, "p.x");
                py = LottieProperty.parse(p.getAsJsonObject().get("y"), new double[] {0}, report, "p.y");
            } else {
                position = LottieProperty.parse(p, new double[] {0, 0}, report, "p");
            }
            if (json.has("sk") || json.has("sa")) {
                double skew = LottieProperty.parse(json.get("sk"), new double[] {0}, report, "sk").scalar(0);
                if (skew != 0) {
                    report.unsupported("sesgado (skew)");
                }
            }
            return new Transform(LottieProperty.parse(json.get("a"), new double[] {0, 0}, report, "a"), position, px, py,
                    LottieProperty.parse(json.get("s"), new double[] {100, 100}, report, "s"),
                    LottieProperty.parse(json.has("r") ? json.get("r") : json.get("rz"), new double[] {0}, report, "r"),
                    LottieProperty.parse(json.get("o"), new double[] {100}, report, "o"));
        }

        private LottieProperty prop(JsonObject json, String key, double... fallback) {
            return LottieProperty.parse(json.get(key), fallback, report, key);
        }

        private void count() throws MediaException {
            if (++nodes > MAX_NODES) {
                throw new MediaException("Lottie demasiado complejo (más de " + MAX_NODES + " elementos)");
            }
        }
    }

    // ---------------------------------------------------------------- dibujo

    private static FrameSequence render(Animation animation, MediaLimits limits, int maxFps) throws MediaException {
        double scale = Math.min(1, Math.min(limits.maxWidth() / animation.width, limits.maxHeight() / animation.height));
        int width = Math.max(1, (int) Math.round(animation.width * scale));
        int height = Math.max(1, (int) Math.round(animation.height * scale));
        double fps = Math.min(animation.fps, Math.max(1, maxFps));
        double seconds = (animation.outPoint - animation.inPoint) / animation.fps;
        int count = Math.max(1, (int) Math.ceil(seconds * fps - 1e-9));
        limits.checkFrames(count, width, height);
        int delay = Math.max(1, (int) Math.round(1000 / fps));

        Map<Integer, Layer> byIndex = new HashMap<>();
        for (Layer layer : animation.layers) {
            byIndex.put(layer.index, layer);
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        List<int[]> frames = new ArrayList<>(count);
        List<Integer> delays = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double frame = animation.inPoint + i * animation.fps / fps;
            Graphics2D g = image.createGraphics();
            try {
                g.setComposite(java.awt.AlphaComposite.Clear);
                g.fillRect(0, 0, width, height);
                g.setComposite(java.awt.AlphaComposite.SrcOver);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                AffineTransform base = AffineTransform.getScaleInstance(width / animation.width, height / animation.height);
                // La primera capa de la lista es la de arriba: se dibuja la última
                for (int l = animation.layers.size() - 1; l >= 0; l--) {
                    drawLayer(g, animation.layers.get(l), byIndex, base, frame);
                }
            } finally {
                g.dispose();
            }
            frames.add(image.getRGB(0, 0, width, height, null, 0, width));
            delays.add(delay);
        }
        return new FrameSequence(width, height, frames, delays);
    }

    private static void drawLayer(Graphics2D g, Layer layer, Map<Integer, Layer> byIndex, AffineTransform base,
                                  double frame) {
        if (layer.type == 3 || frame < layer.inPoint || frame >= layer.outPoint) {
            return;
        }
        double local = layer.localFrame(frame);
        AffineTransform tx = new AffineTransform(base);
        tx.concatenate(layerMatrix(layer, byIndex, frame, 0));
        double opacity = layer.transform.opacity(local);
        if (opacity <= 0) {
            return;
        }
        if (layer.type == 1) {
            g.setTransform(tx);
            g.setColor(withAlpha(layer.solidColor, opacity));
            g.fill(new java.awt.geom.Rectangle2D.Double(0, 0, layer.solidWidth, layer.solidHeight));
        } else if (layer.shapes != null) {
            drawGroup(g, layer.shapes, tx, opacity, local);
        }
    }

    private static AffineTransform layerMatrix(Layer layer, Map<Integer, Layer> byIndex, double frame, int depth) {
        AffineTransform own = layer.transform.matrix(layer.localFrame(frame));
        Layer parent = layer.parent == null ? null : byIndex.get(layer.parent);
        if (parent == null || parent == layer || depth > MAX_DEPTH) {
            return own;
        }
        AffineTransform m = layerMatrix(parent, byIndex, frame, depth + 1);
        m.concatenate(own);
        return m;
    }

    private static void drawGroup(Graphics2D g, Group group, AffineTransform parentTx, double parentOpacity,
                                  double frame) {
        AffineTransform tx = new AffineTransform(parentTx);
        double opacity = parentOpacity;
        if (group.transform != null) {
            tx.concatenate(group.transform.matrix(frame));
            opacity *= group.transform.opacity(frame);
        }
        if (opacity <= 0) {
            return;
        }
        List<Item> items = group.items;
        // El primer elemento queda arriba: de abajo hacia arriba. Un estilo pinta las formas que tiene antes.
        for (int k = items.size() - 1; k >= 0; k--) {
            Item item = items.get(k);
            switch (item) {
                case Group nested -> drawGroup(g, nested, tx, opacity, frame);
                case Fill fill -> {
                    Color color = color(fill.color.value(frame), opacity * fill.opacity.scalar(frame) / 100.0);
                    for (Placed placed : geometry(items.subList(0, k), tx, frame)) {
                        Shape shape = placed.shape;
                        if (fill.evenOdd && shape instanceof Path2D path) {
                            path.setWindingRule(Path2D.WIND_EVEN_ODD);
                        }
                        g.setTransform(placed.transform);
                        g.setColor(color);
                        g.fill(shape);
                    }
                }
                case Stroke stroke -> {
                    Color color = color(stroke.color.value(frame), opacity * stroke.opacity.scalar(frame) / 100.0);
                    float width = (float) Math.max(0, stroke.width.scalar(frame));
                    if (width <= 0) {
                        continue;
                    }
                    BasicStroke basic = new BasicStroke(width, cap(stroke.cap), join(stroke.join), stroke.miter);
                    for (Placed placed : geometry(items.subList(0, k), tx, frame)) {
                        g.setTransform(placed.transform);
                        g.setColor(color);
                        g.setStroke(basic);
                        g.draw(placed.shape);
                    }
                }
                case Geometry ignored -> { }
            }
        }
    }

    private record Placed(Shape shape, AffineTransform transform) {
    }

    private static List<Placed> geometry(List<Item> items, AffineTransform tx, double frame) {
        List<Placed> out = new ArrayList<>();
        for (Item item : items) {
            if (item instanceof Geometry geometry) {
                out.add(new Placed(geometry.shape(frame), tx));
            } else if (item instanceof Group nested) {
                AffineTransform inner = new AffineTransform(tx);
                if (nested.transform != null) {
                    inner.concatenate(nested.transform.matrix(frame));
                }
                out.addAll(geometry(nested.items, inner, frame));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- utilidades

    private static Path2D toPath2D(double[] flat, boolean closed) {
        Path2D.Double path = new Path2D.Double();
        int n = flat.length / 6;
        if (n == 0) {
            return path;
        }
        path.moveTo(flat[0], flat[1]);
        for (int j = 1; j < n; j++) {
            segment(path, flat, j - 1, j);
        }
        if (closed) {
            segment(path, flat, n - 1, 0);
            path.closePath();
        }
        return path;
    }

    private static void segment(Path2D path, double[] f, int from, int to) {
        int a = from * 6, b = to * 6;
        path.curveTo(f[a] + f[a + 4], f[a + 1] + f[a + 5], f[b] + f[b + 2], f[b + 1] + f[b + 3], f[b], f[b + 1]);
    }

    private static Color color(double[] rgba, double opacity) {
        double max = 1;
        for (int i = 0; i < Math.min(3, rgba.length); i++) {
            max = Math.max(max, rgba[i]);
        }
        double div = max > 1 ? 255 : 1; // algunos exportadores antiguos usan 0-255
        float r = (float) Math.clamp(at(rgba, 0) / div, 0, 1);
        float gr = (float) Math.clamp(at(rgba, 1) / div, 0, 1);
        float b = (float) Math.clamp(at(rgba, 2) / div, 0, 1);
        float a = (float) Math.clamp((rgba.length > 3 ? rgba[3] / div : 1) * opacity, 0, 1);
        return new Color(r, gr, b, a);
    }

    private static Color withAlpha(Color c, double opacity) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(Math.clamp(opacity, 0, 1) * 255));
    }

    private static Color hexColor(String hex) {
        try {
            return new Color(Integer.parseInt(hex.replace("#", ""), 16));
        } catch (NumberFormatException e) {
            return Color.BLACK;
        }
    }

    private static int cap(int lc) {
        return switch (lc) {
            case 1 -> BasicStroke.CAP_BUTT;
            case 3 -> BasicStroke.CAP_SQUARE;
            default -> BasicStroke.CAP_ROUND;
        };
    }

    private static int join(int lj) {
        return switch (lj) {
            case 1 -> BasicStroke.JOIN_MITER;
            case 3 -> BasicStroke.JOIN_BEVEL;
            default -> BasicStroke.JOIN_ROUND;
        };
    }

    private static double num(JsonObject json, String key, double fallback) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : fallback;
    }

    private static double at(double[] v, int i) {
        return i < v.length ? v[i] : 0;
    }
}

