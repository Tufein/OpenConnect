import Toybox.Application;
import Toybox.Lang;

// The choices of a face, widget and glance. In an app from the Connect IQ store the owner changes them in Garmin's
// Connect IQ app (resources/settings, defaults in resources/properties); an app built with watch/build.sh starts with
// the values it was built with. Whatever cannot be read or is not valid falls back to Config, written by build.sh, so
// a bad setting never leaves the screen empty.
(:glance)
module Prefs {
    const FIELD_NAMES = ["battery", "heart", "stress", "steps", "floors"];

    function read(key) {
        try {
            return Application.Properties.getValue(key);
        } catch (e) {
            return null;
        }
    }

    function design() {
        var v = read("Design");
        if ((v instanceof String) && (v.equals("clean") || v.equals("dashboard") || v.equals("retro"))) {
            return v;
        }
        return Config.DESIGN;
    }

    // The accent colour as 0xRRGGBB.
    function color() {
        var v = read("Colour");
        if ((v instanceof Number) && v >= 0 && v <= 0xFFFFFF) {
            return v;
        }
        return Config.COLOR;
    }

    // Four different known field names, the first one the big one; otherwise the ones built in.
    function fields() {
        var chosen = [];
        for (var i = 1; i <= 4; i++) {
            var v = read("Field" + i);
            if (!(v instanceof String) || !has(FIELD_NAMES, v) || has(chosen, v)) {
                return Config.FIELDS;
            }
            chosen.add(v);
        }
        return chosen;
    }

    function has(list, name) {
        for (var i = 0; i < list.size(); i++) {
            if (list[i].equals(name)) {
                return true;
            }
        }
        return false;
    }
}
