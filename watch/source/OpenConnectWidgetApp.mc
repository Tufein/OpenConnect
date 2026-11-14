import Toybox.Application;
import Toybox.WatchUi;

// The widget: one screen with today's numbers (and a Body Battery line of the last 12 hours) plus a glance for the
// glance list. Same build choices as the face: colour and fields come from gen/source/Config.mc.
(:glance)
class OpenConnectWidgetApp extends Application.AppBase {
    function initialize() {
        AppBase.initialize();
    }

    function getInitialView() {
        return [ new WidgetView() ];
    }

    (:glance)
    function getGlanceView() {
        return [ new OpenConnectGlance() ];
    }
}
