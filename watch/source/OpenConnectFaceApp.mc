import Toybox.Application;
import Toybox.WatchUi;

// OpenConnect watch face. The design, colour and fields are fixed when the face is built (watch/build.sh writes
// gen/source/Config.mc), so the face needs neither a phone nor Garmin's settings screen.
class OpenConnectFaceApp extends Application.AppBase {
    function initialize() {
        AppBase.initialize();
    }

    function getInitialView() {
        return [ new FaceView() ];
    }
}
