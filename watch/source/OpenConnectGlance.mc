import Toybox.Graphics;
import Toybox.WatchUi;

// The glance shows the first three fields on two short lines. Everything a glance uses (this class, Data and Config)
// carries the (:glance) annotation, because a glance runs in very little memory.
(:glance)
class OpenConnectGlance extends WatchUi.GlanceView {
    function initialize() {
        GlanceView.initialize();
    }

    function onUpdate(dc) {
        var h = dc.getHeight();
        var fields = Prefs.fields();
        dc.setColor(Prefs.color(), Graphics.COLOR_TRANSPARENT);
        dc.drawText(0, h * 28 / 100, Graphics.FONT_GLANCE, "OPENCONNECT", Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        var line = "";
        for (var i = 0; i < 3; i++) {
            line = line + Data.label(fields[i]) + " " + Data.text(fields[i]);
            if (i < 2) {
                line = line + "  ";
            }
        }
        dc.drawText(0, h * 72 / 100, Graphics.FONT_GLANCE, line, Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);
    }
}
