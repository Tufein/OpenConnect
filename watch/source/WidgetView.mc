import Toybox.WatchUi;

class WidgetView extends WatchUi.View {
    function initialize() {
        View.initialize();
    }

    function onUpdate(dc) {
        Faces.widget(dc);
    }
}
