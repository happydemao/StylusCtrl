package app.stylusctrl;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The daemon's json profile. Unknown keys are dropped on save. */
public class Profile {
    public String vendor = "0531";
    public String product = "0104";
    /** Evdev name from getevent. Empty means vendor and product are enough. */
    public String matchName = "";
    public String mode = "fit";
    public String crop = "center";
    public boolean hasRect;
    public int[] rect = new int[] {0, 0, 15200, 9500};
    public boolean hasScreen;
    public double[] screen = new double[] {0, 0, 1, 1};
    /** 0, 90, 180, or 270. The tablet's own rotation, independent of the screen. */
    public int rotation;
    /** Pixel size and Surface rotation (0–3) of the internal display. The daemon maps from this. */
    public boolean hasDisplay;
    public int displayW;
    public int displayH;
    public int displayRotation;
    /** Axis max the rect was edited against. Missing on old files; the app assumes 15200×9500. */
    public boolean hasSpan;
    public int spanX = 15200;
    public int spanY = 9500;
    public Feel external = Feel.tabletDefault();
    public Feel internal = new Feel();
    public boolean internalOn;
    /** 0, 90, 180, or 270. The panel sensor's mount, not a preset and not the screen. */
    public int internalOrientation;
    /** Publish the virtual device as a pointer. The finger screen already owns the panel. */
    public boolean internalPointer;
    /** When set, this replaces the built-in sec_e-pen / I2C rule. */
    public boolean hasInternalMatch;
    public String internalName = "";
    public String internalPrefix = "";
    public String internalVendor = "";
    public String internalProduct = "";
    public String internalBus = "";
    public boolean internalDirectSet;
    public boolean internalDirect;
    public boolean internalPenSet;
    public boolean internalPen;
    public boolean internalMultitouch;
    /** Set when the file has a presets object or a devices array. */
    public boolean hasPresetTable;
    public final java.util.List<PresetStore.Item> parsedPresets = new java.util.ArrayList<>();
    public final java.util.List<PresetStore.Device> parsedDevices = new java.util.ArrayList<>();

    /** The old cubic curve (0,0)(0.4,0.7)(0.4,0.7)(1,1) sampled at five knots. */
    public static final float[] DEFAULT_CURVE =
            new float[] {0f, 0f, 0.25f, 0.424f, 0.5f, 0.717f, 0.75f, 0.873f, 1f, 1f};

    public static final int MAX_KNOTS = 12;
    /** Keeps config.json small enough for one su argument, and matches the preset cap. */
    public static final int MAX_CURVES = 8;

    /** One named pressure curve. pts holds x,y pairs in 0..1, first x 0, last x 1. */
    public static final class Curve {
        public String name = "";
        public float[] pts;

        public Curve() {
            this("", DEFAULT_CURVE.clone());
        }

        public Curve(String name, float[] pts) {
            this.name = name == null ? "" : name;
            this.pts = pts == null || pts.length < 4 ? DEFAULT_CURVE.clone() : pts.clone();
        }

        public Curve copy() {
            return new Curve(name, pts);
        }
    }

    /** Pressure curves and side buttons for one pen. Mapping is not part of this. */
    public static final class Feel {
        public double deadzone;
        public double ceiling = 1;
        public final List<Curve> curves = new ArrayList<>();
        public int active;
        public String stylusAction = "passthrough";
        public final List<String> stylusKeys = new ArrayList<>();
        public String rubberAction = "eraser";
        public final List<String> rubberKeys = new ArrayList<>();
        public boolean eraserWins = true;

        public Feel() {
            resetCurves();
        }

        public static Feel tabletDefault() {
            Feel feel = new Feel();
            feel.stylusAction = "shortcut";
            feel.stylusKeys.add("CTRL");
            feel.stylusKeys.add("Z");
            return feel;
        }

        public void resetCurves() {
            curves.clear();
            curves.add(new Curve("", DEFAULT_CURVE.clone()));
            active = 0;
        }

        public Curve activeCurve() {
            if (curves.isEmpty()) curves.add(new Curve("", DEFAULT_CURVE.clone()));
            if (active < 0 || active >= curves.size()) active = 0;
            return curves.get(active);
        }

        public float[] points() {
            return activeCurve().pts;
        }

        public void copyFrom(Feel other) {
            deadzone = other.deadzone;
            ceiling = other.ceiling;
            curves.clear();
            for (Curve c : other.curves) curves.add(c.copy());
            active = other.active;
            stylusAction = other.stylusAction;
            stylusKeys.clear();
            stylusKeys.addAll(other.stylusKeys);
            rubberAction = other.rubberAction;
            rubberKeys.clear();
            rubberKeys.addAll(other.rubberKeys);
            eraserWins = other.eraserWins;
        }
    }

    public static Profile parse(String text) throws Exception {
        JSONObject root = new JSONObject(text);
        Profile p = new Profile();
        JSONObject match = root.optJSONObject("match");
        if (match != null) {
            p.vendor = match.optString("vendor", p.vendor);
            p.product = match.optString("product", p.product);
            p.matchName = match.optString("name", "");
        }
        JSONObject mapping = root.optJSONObject("mapping");
        if (mapping != null) {
            p.mode = mapping.optString("mode", p.mode);
            p.crop = mapping.optString("crop", p.crop);
            JSONArray rect = mapping.optJSONArray("rect");
            if (rect != null && rect.length() == 4) {
                p.hasRect = true;
                for (int i = 0; i < 4; i++) p.rect[i] = rect.getInt(i);
            }
            JSONArray screen = mapping.optJSONArray("screen");
            if (screen != null && screen.length() == 4) {
                p.hasScreen = true;
                for (int i = 0; i < 4; i++) p.screen[i] = screen.getDouble(i);
            }
            JSONArray span = mapping.optJSONArray("span");
            if (span != null && span.length() == 2) {
                p.hasSpan = true;
                p.spanX = span.getInt(0);
                p.spanY = span.getInt(1);
            }
            int rotation = mapping.optInt("rotation", 0);
            if (rotation == 1) rotation = 90;
            else if (rotation == 2) rotation = 180;
            else if (rotation == 3) rotation = 270;
            if (rotation == 90 || rotation == 180 || rotation == 270) p.rotation = rotation;
        }
        JSONObject display = root.optJSONObject("display");
        if (display != null) {
            p.displayW = display.optInt("width", 0);
            p.displayH = display.optInt("height", 0);
            int turn = display.optInt("rotation", 0);
            if (turn == 90) turn = 1;
            else if (turn == 180) turn = 2;
            else if (turn == 270) turn = 3;
            if (turn >= 0 && turn <= 3) p.displayRotation = turn;
            p.hasDisplay = p.displayW > 0 && p.displayH > 0;
        }
        readFeelPressure(root.optJSONObject("pressure"), p.external);
        readFeelButtons(root.optJSONObject("buttons"), root.optJSONObject("tools"), p.external);
        JSONObject internal = root.optJSONObject("internal");
        if (internal != null) {
            p.internalOn = internal.optBoolean("enabled", false);
            int mount = internal.optInt("orientation", 0);
            if (mount == 1) mount = 90;
            else if (mount == 2) mount = 180;
            else if (mount == 3) mount = 270;
            if (mount == 90 || mount == 180 || mount == 270) p.internalOrientation = mount;
            p.internalPointer = internal.optBoolean("pointer", false);
            readInternalMatch(internal.optJSONObject("match"), p);
            boolean ownPressure = readFeelPressure(internal.optJSONObject("pressure"), p.internal);
            boolean ownButtons = readFeelButtons(internal.optJSONObject("buttons"), internal.optJSONObject("tools"), p.internal);
            if (!ownPressure) copyPressure(p.external, p.internal);
            if (!ownButtons) copyButtons(p.external, p.internal);
        } else {
            p.internal.copyFrom(p.external);
        }
        JSONObject presets = root.optJSONObject("presets");
        JSONArray devices = root.optJSONArray("devices");
        if (presets != null || devices != null) {
            p.hasPresetTable = true;
            if (presets != null) {
                java.util.Iterator<String> keys = presets.keys();
                while (keys.hasNext()) {
                    String id = keys.next();
                    JSONObject body = presets.optJSONObject(id);
                    if (body != null) p.parsedPresets.add(readPreset(id, body));
                }
            }
            if (devices != null) {
                for (int i = 0; i < devices.length(); i++) {
                    JSONObject one = devices.optJSONObject(i);
                    if (one != null) p.parsedDevices.add(readDevice(one));
                }
            }
        }
        return p;
    }

    static PresetStore.Item readPreset(String id, JSONObject body) throws Exception {
        PresetStore.Item item = new PresetStore.Item();
        item.id = id;
        item.name = id;
        item.external = new Feel();
        JSONObject mapping = body.optJSONObject("mapping");
        if (mapping != null) {
            item.mode = mapping.optString("mode", item.mode);
            item.crop = mapping.optString("crop", item.crop);
            JSONArray rect = mapping.optJSONArray("rect");
            if (rect != null && rect.length() == 4) {
                item.hasRect = true;
                for (int i = 0; i < 4; i++) item.rect[i] = rect.getInt(i);
            }
            JSONArray screen = mapping.optJSONArray("screen");
            if (screen != null && screen.length() == 4) {
                item.hasScreen = true;
                for (int i = 0; i < 4; i++) item.screen[i] = screen.getDouble(i);
            }
            JSONArray span = mapping.optJSONArray("span");
            if (span != null && span.length() == 2) {
                item.hasSpan = true;
                item.spanX = span.getInt(0);
                item.spanY = span.getInt(1);
            }
            int rotation = mapping.optInt("rotation", 0);
            if (rotation == 1) rotation = 90;
            else if (rotation == 2) rotation = 180;
            else if (rotation == 3) rotation = 270;
            if (rotation == 90 || rotation == 180 || rotation == 270) item.rotation = rotation;
        }
        readFeelPressure(body.optJSONObject("pressure"), item.external);
        readFeelButtons(body.optJSONObject("buttons"), body.optJSONObject("tools"), item.external);
        JSONObject internal = body.optJSONObject("internal");
        if (internal != null) {
            boolean ownPressure = readFeelPressure(internal.optJSONObject("pressure"), item.internal);
            boolean ownButtons = readFeelButtons(internal.optJSONObject("buttons"), internal.optJSONObject("tools"), item.internal);
            if (!ownPressure) copyPressure(item.external, item.internal);
            if (!ownButtons) copyButtons(item.external, item.internal);
        } else {
            item.internal.copyFrom(item.external);
        }
        return item;
    }

    static PresetStore.Device readDevice(JSONObject one) {
        PresetStore.Device device = new PresetStore.Device();
        device.id = one.optString("id", "");
        device.baseline = one.optString("baseline", "");
        JSONObject match = one.optJSONObject("match");
        if (match != null) {
            device.vendor = match.optString("vendor", "");
            device.product = match.optString("product", "");
            device.name = match.optString("name", "");
            device.namePrefix = match.optString("namePrefix", "");
            device.bus = match.optString("bus", "");
            if (match.has("direct")) {
                device.directSet = true;
                device.direct = match.optBoolean("direct");
            }
            if (match.has("pen")) {
                device.penSet = true;
                device.pen = match.optBoolean("pen");
            }
            if (match.has("multitouch")) {
                device.multitouchSet = true;
                device.multitouch = match.optBoolean("multitouch");
            }
        }
        JSONObject apps = one.optJSONObject("apps");
        if (apps != null) {
            java.util.Iterator<String> keys = apps.keys();
            while (keys.hasNext()) {
                String pkg = keys.next();
                device.apps.put(pkg, apps.optString(pkg, ""));
            }
        }
        return device;
    }

    public void copyFeelTo(PresetStore.Item item) {
        item.mode = mode;
        item.crop = crop;
        item.rotation = rotation;
        item.hasRect = hasRect;
        item.rect = rect.clone();
        item.hasScreen = hasScreen;
        item.screen = screen.clone();
        item.hasSpan = hasSpan;
        item.spanX = spanX;
        item.spanY = spanY;
        item.external.copyFrom(external);
        item.internal.copyFrom(internal);
    }

    /** Mapping and both feels. Display, match, and whether the internal pen is grabbed stay. */
    public void copyFeelFrom(PresetStore.Item item) {
        mode = item.mode;
        crop = item.crop;
        rotation = item.rotation;
        hasRect = item.hasRect;
        rect = item.rect.clone();
        hasScreen = item.hasScreen;
        screen = item.screen.clone();
        hasSpan = item.hasSpan;
        spanX = item.spanX;
        spanY = item.spanY;
        external.copyFrom(item.external);
        internal.copyFrom(item.internal);
    }

    private static void readInternalMatch(JSONObject match, Profile p) {
        if (match == null) return;
        p.internalName = match.optString("name", "");
        p.internalPrefix = match.optString("namePrefix", "");
        p.internalVendor = match.optString("vendor", "");
        p.internalProduct = match.optString("product", "");
        p.internalBus = match.optString("bus", "");
        if (match.has("direct")) {
            p.internalDirectSet = true;
            p.internalDirect = match.optBoolean("direct");
        }
        if (match.has("pen")) {
            p.internalPenSet = true;
            p.internalPen = match.optBoolean("pen");
        }
        if (match.has("multitouch")) p.internalMultitouch = match.optBoolean("multitouch");
        p.hasInternalMatch = !p.internalName.isEmpty() || !p.internalPrefix.isEmpty() || !p.internalVendor.isEmpty()
                || !p.internalProduct.isEmpty() || !p.internalBus.isEmpty() || p.internalDirectSet || p.internalPenSet
                || match.has("multitouch");
    }

    private static boolean readFeelPressure(JSONObject pressure, Feel feel) throws Exception {
        if (pressure == null) return false;
        feel.deadzone = pressure.optDouble("deadzone", 0);
        feel.ceiling = pressure.optDouble("ceiling", 1);
        feel.curves.clear();
        JSONArray bank = pressure.optJSONArray("curves");
        if (bank != null) {
            for (int i = 0; i < bank.length() && feel.curves.size() < MAX_CURVES; i++) {
                JSONObject one = bank.optJSONObject(i);
                if (one == null) continue;
                float[] pts = readPoints(one.optJSONArray("points"));
                if (pts != null) feel.curves.add(new Curve(one.optString("name", ""), pts));
            }
        }
        if (feel.curves.isEmpty()) {
            float[] pts = readPoints(pressure.optJSONArray("points"));
            if (pts != null) feel.curves.add(new Curve("", pts));
        }
        if (feel.curves.isEmpty()) {
            JSONArray curve = pressure.optJSONArray("curve");
            if (curve != null && curve.length() >= 6) feel.curves.add(new Curve("", fromBezier(curve)));
        }
        if (feel.curves.isEmpty()) feel.curves.add(new Curve("", DEFAULT_CURVE.clone()));
        int index = pressure.optInt("active", 0);
        if (index < 0 || index >= feel.curves.size()) index = 0;
        feel.active = index;
        return true;
    }

    /** Reads a flat x,y knot array. Rejects anything the daemon would also reject. */
    static float[] readPoints(JSONArray arr) {
        if (arr == null || arr.length() < 4 || arr.length() > MAX_KNOTS * 2 || (arr.length() % 2) != 0) return null;
        float[] pts = new float[arr.length()];
        for (int i = 0; i < arr.length(); i += 2) {
            float x = (float) arr.optDouble(i, 0);
            float y = (float) arr.optDouble(i + 1, 0);
            if (i > 0 && x <= pts[i - 2]) return null;
            pts[i] = Math.max(0f, Math.min(1f, x));
            pts[i + 1] = Math.max(0f, Math.min(1f, y));
        }
        if (pts[0] != 0f || pts[pts.length - 2] != 1f) return null;
        return pts;
    }

    /** Old configs keep a cubic Bezier in "curve". Sample it into knots instead of dropping it. */
    static float[] fromBezier(JSONArray curve) {
        double[] c = new double[8];
        if (curve.length() >= 8) {
            for (int i = 0; i < 8; i++) c[i] = curve.optDouble(i, 0);
        } else {
            c[0] = curve.optDouble(0, 0);
            c[1] = curve.optDouble(1, 0);
            c[2] = curve.optDouble(2, 0);
            c[3] = curve.optDouble(3, 0);
            c[4] = curve.optDouble(2, 0);
            c[5] = curve.optDouble(3, 0);
            c[6] = curve.optDouble(4, 0);
            c[7] = curve.optDouble(5, 1);
        }
        float[] pts = new float[10];
        float[] xs = new float[] {0f, 0.25f, 0.5f, 0.75f, 1f};
        for (int i = 0; i < 5; i++) {
            float x = xs[i];
            double a = 0;
            double b = 1;
            for (int k = 0; k < 40; k++) {
                double mid = (a + b) / 2;
                double bx = bezierAt(mid, c[0], c[2], c[4], c[6]);
                if (bx < x) a = mid;
                else b = mid;
            }
            pts[i * 2] = x;
            pts[i * 2 + 1] = (float) Math.max(0, Math.min(1, bezierAt((a + b) / 2, c[1], c[3], c[5], c[7])));
        }
        return pts;
    }

    static double bezierAt(double s, double p0, double p1, double p2, double p3) {
        double t = 1 - s;
        return t * t * t * p0 + 3 * t * t * s * p1 + 3 * t * s * s * p2 + s * s * s * p3;
    }

    private static boolean readFeelButtons(JSONObject buttons, JSONObject tools, Feel feel) throws Exception {
        if (buttons == null && tools == null) return false;
        if (buttons != null) {
            readButton(buttons.optJSONObject("BTN_STYLUS"), true, feel);
            readButton(buttons.optJSONObject("BTN_TOOL_RUBBER"), false, feel);
        }
        if (tools != null) feel.eraserWins = tools.optBoolean("eraserWins", true);
        return true;
    }

    private static void copyPressure(Feel from, Feel to) {
        to.deadzone = from.deadzone;
        to.ceiling = from.ceiling;
        to.curves.clear();
        for (Curve c : from.curves) to.curves.add(c.copy());
        to.active = from.active;
    }

    private static void copyButtons(Feel from, Feel to) {
        to.stylusAction = from.stylusAction;
        to.stylusKeys.clear();
        to.stylusKeys.addAll(from.stylusKeys);
        to.rubberAction = from.rubberAction;
        to.rubberKeys.clear();
        to.rubberKeys.addAll(from.rubberKeys);
        to.eraserWins = from.eraserWins;
    }

    private static void readButton(JSONObject obj, boolean stylus, Feel feel) throws Exception {
        if (obj == null) return;
        String action = obj.optString("action", stylus ? "passthrough" : "eraser");
        List<String> keys = new ArrayList<>();
        JSONArray arr = obj.optJSONArray("keys");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) keys.add(arr.getString(i));
        }
        if (stylus) {
            feel.stylusAction = action;
            if (!keys.isEmpty()) {
                feel.stylusKeys.clear();
                feel.stylusKeys.addAll(keys);
            }
        } else {
            feel.rubberAction = action;
            feel.rubberKeys.clear();
            feel.rubberKeys.addAll(keys);
        }
    }

    public String toJson() {
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        if (hasDisplay) {
            b.append("  \"display\": { \"width\": ").append(displayW).append(", \"height\": ").append(displayH)
                    .append(", \"rotation\": ").append(displayRotation).append(" },\n");
        }
        b.append("  \"match\": { \"vendor\": \"").append(jsonString(vendor)).append("\", \"product\": \"")
                .append(jsonString(product)).append("\"");
        if (matchName != null && !matchName.isEmpty()) {
            b.append(", \"name\": \"").append(jsonString(matchName)).append("\"");
        }
        b.append(" },\n");
        b.append("  \"mapping\": { \"mode\": \"").append(mode).append("\", \"crop\": \"")
                .append(crop).append("\", \"rotation\": ").append(rotation);
        if (hasRect) {
            b.append(", \"rect\": [").append(rect[0]).append(", ").append(rect[1]).append(", ")
                    .append(rect[2]).append(", ").append(rect[3]).append("]");
        }
        if (hasScreen) {
            b.append(", \"screen\": [");
            for (int i = 0; i < 4; i++) {
                if (i > 0) b.append(", ");
                b.append(String.format(Locale.US, "%.4f", screen[i]));
            }
            b.append("]");
        }
        if (hasSpan) {
            b.append(", \"span\": [").append(spanX).append(", ").append(spanY).append("]");
        }
        b.append(" },\n");
        b.append("  \"pressure\": ").append(pressureJson(external)).append(",\n");
        b.append("  \"buttons\": ").append(buttonsJson(external)).append(",\n");
        b.append("  \"tools\": { \"eraserWins\": ").append(external.eraserWins).append(" },\n");
        b.append("  \"internal\": {\n");
        b.append("    \"enabled\": ").append(internalOn);
        b.append(",\n    \"orientation\": ").append(internalOrientation);
        b.append(",\n    \"pointer\": ").append(internalPointer);
        if (hasInternalMatch) b.append(",\n    \"match\": ").append(internalMatchJson());
        b.append(",\n");
        b.append("    \"pressure\": ").append(pressureJson(internal)).append(",\n");
        b.append("    \"buttons\": ").append(buttonsJson(internal)).append(",\n");
        b.append("    \"tools\": { \"eraserWins\": ").append(internal.eraserWins).append(" }\n");
        b.append("  }");
        PresetStore.appendConfig(b);
        b.append("\n}\n");
        return b.toString();
    }

    static String presetBody(PresetStore.Item item) {
        StringBuilder b = new StringBuilder();
        b.append("{ \"mapping\": { \"mode\": \"").append(item.mode).append("\", \"crop\": \"")
                .append(item.crop).append("\", \"rotation\": ").append(item.rotation);
        if (item.hasRect) {
            b.append(", \"rect\": [").append(item.rect[0]).append(", ").append(item.rect[1]).append(", ")
                    .append(item.rect[2]).append(", ").append(item.rect[3]).append("]");
        }
        if (item.hasScreen) {
            b.append(", \"screen\": [");
            for (int i = 0; i < 4; i++) {
                if (i > 0) b.append(", ");
                b.append(String.format(Locale.US, "%.4f", item.screen[i]));
            }
            b.append("]");
        }
        if (item.hasSpan) {
            b.append(", \"span\": [").append(item.spanX).append(", ").append(item.spanY).append("]");
        }
        b.append(" }, \"pressure\": ").append(pressureJson(item.external));
        b.append(", \"buttons\": ").append(buttonsJson(item.external));
        b.append(", \"tools\": { \"eraserWins\": ").append(item.external.eraserWins).append(" }");
        b.append(", \"internal\": { \"pressure\": ").append(pressureJson(item.internal));
        b.append(", \"buttons\": ").append(buttonsJson(item.internal));
        b.append(", \"tools\": { \"eraserWins\": ").append(item.internal.eraserWins).append(" } } }");
        return b.toString();
    }

    private String internalMatchJson() {
        StringBuilder b = new StringBuilder();
        b.append("{ ");
        boolean comma = false;
        comma = jsonField(b, comma, "name", internalName);
        comma = jsonField(b, comma, "namePrefix", internalPrefix);
        comma = jsonField(b, comma, "vendor", internalVendor);
        comma = jsonField(b, comma, "product", internalProduct);
        comma = jsonField(b, comma, "bus", internalBus);
        if (internalDirectSet) {
            if (comma) b.append(", ");
            b.append("\"direct\": ").append(internalDirect);
            comma = true;
        }
        if (internalPenSet) {
            if (comma) b.append(", ");
            b.append("\"pen\": ").append(internalPen);
            comma = true;
        }
        if (internalMultitouch) {
            if (comma) b.append(", ");
            b.append("\"multitouch\": true");
        }
        b.append(" }");
        return b.toString();
    }

    private static boolean jsonField(StringBuilder b, boolean comma, String key, String value) {
        if (value == null || value.isEmpty()) return comma;
        if (comma) b.append(", ");
        b.append("\"").append(key).append("\": \"").append(jsonString(value)).append("\"");
        return true;
    }

    static String jsonString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String pressureJson(Feel feel) {
        Profile.Curve active = feel.activeCurve();
        StringBuilder b = new StringBuilder();
        b.append("{ \"deadzone\": ").append(String.format(Locale.US, "%.4f", feel.deadzone));
        b.append(", \"ceiling\": ").append(String.format(Locale.US, "%.4f", feel.ceiling));
        b.append(", \"active\": ").append(feel.active);
        b.append(", \"points\": ").append(pointsJson(active.pts));
        b.append(", \"curves\": [");
        for (int i = 0; i < feel.curves.size(); i++) {
            if (i > 0) b.append(", ");
            Profile.Curve c = feel.curves.get(i);
            b.append("{ \"name\": \"").append(jsonString(c.name)).append("\", \"points\": ")
                    .append(pointsJson(c.pts)).append(" }");
        }
        b.append("] }");
        return b.toString();
    }

    private static String pointsJson(float[] pts) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < pts.length; i++) {
            if (i > 0) b.append(", ");
            b.append(String.format(Locale.US, "%.4f", pts[i]));
        }
        b.append("]");
        return b.toString();
    }

    private static String buttonsJson(Feel feel) {
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append("    \"BTN_STYLUS\": ").append(buttonJson(feel.stylusAction, feel.stylusKeys)).append(",\n");
        b.append("    \"BTN_TOOL_RUBBER\": ").append(buttonJson(feel.rubberAction, feel.rubberKeys)).append("\n");
        b.append("  }");
        return b.toString();
    }

    private static String buttonJson(String action, List<String> keys) {
        StringBuilder b = new StringBuilder();
        b.append("{ \"action\": \"").append(action).append("\"");
        if ("shortcut".equals(action) && keys != null && !keys.isEmpty()) {
            b.append(", \"keys\": [");
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) b.append(", ");
                b.append("\"").append(keys.get(i)).append("\"");
            }
            b.append("]");
        }
        b.append(" }");
        return b.toString();
    }

    /** Rect is in the rotated tablet space. Full-span rects jump to the new full span; partial ones scale. */
    public static int[] scaleRect(int[] rect, int rotation, int oldX, int oldY, int newX, int newY) {
        boolean swap = rotation == 90 || rotation == 270;
        int oldW = swap ? oldY : oldX;
        int oldH = swap ? oldX : oldY;
        int newW = swap ? newY : newX;
        int newH = swap ? newX : newY;
        if (rect[0] <= 0 && rect[1] <= 0 && rect[2] >= oldW && rect[3] >= oldH) {
            return new int[] {0, 0, newW, newH};
        }
        return new int[] {
                scale(rect[0], oldW, newW),
                scale(rect[1], oldH, newH),
                scale(rect[2], oldW, newW),
                scale(rect[3], oldH, newH)
        };
    }

    private static int scale(int value, int oldMax, int newMax) {
        if (oldMax <= 0) return value;
        return (int) Math.round(value * (double) newMax / (double) oldMax);
    }
}
