import Toybox.System;
import Toybox.WatchUi;

class FaceView extends WatchUi.WatchFace {
    var _sleeping = false;

    function initialize() {
        WatchFace.initialize();
    }

    function onEnterSleep() {
        _sleeping = true;
        WatchUi.requestUpdate();
    }

    function onExitSleep() {
        _sleeping = false;
        WatchUi.requestUpdate();
    }

    function onUpdate(dc) {
        // An AMOLED screen that needs burn-in protection shows only the time while the wrist is down.
        var settings = System.getDeviceSettings();
        if (_sleeping && (settings has :requiresBurnInProtection) && settings.requiresBurnInProtection) {
            Faces.sleeping(dc);
        } else if (Prefs.design().equals("dashboard")) {
            Faces.dashboard(dc);
        } else if (Prefs.design().equals("retro")) {
            Faces.retro(dc);
        } else {
            Faces.clean(dc);
        }
    }
}
