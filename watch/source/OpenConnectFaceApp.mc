import Toybox.Application;
import Toybox.WatchUi;

// OpenConnect watch face. Design, colour and fields start as built by watch/build.sh (gen/source/Config.mc) and, for
// an app from the Connect IQ store, can be changed in Garmin's Connect IQ app (see Prefs.mc).
class OpenConnectFaceApp extends Application.AppBase {
    function initialize() {
        AppBase.initialize();
    }

    function getInitialView() {
        return [ new FaceView() ];
    }

    function onSettingsChanged() {
        WatchUi.requestUpdate();
    }
}
