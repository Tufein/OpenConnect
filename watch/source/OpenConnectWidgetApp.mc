import Toybox.Application;
import Toybox.WatchUi;

// The widget: one screen with today's numbers (and a Body Battery line of the last 12 hours) plus a glance for the
// glance list. Colour and fields come from Prefs: the settings in Garmin's Connect IQ app, else gen/source/Config.mc.
(:glance)
class OpenConnectWidgetApp extends Application.AppBase {
    function initialize() {
        AppBase.initialize();
    }

    function getInitialView() {
        return [ new WidgetView() ];
    }

    function onSettingsChanged() {
        WatchUi.requestUpdate();
    }

    (:glance)
    function getGlanceView() {
        return [ new OpenConnectGlance() ];
    }
}
