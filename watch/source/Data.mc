import Toybox.Activity;
import Toybox.ActivityMonitor;
import Toybox.Lang;
import Toybox.SensorHistory;
import Toybox.UserProfile;

// The values a face can show. Every one of them is something the watch itself knows, so no phone is needed. A value
// the watch cannot give right now is null and the face shows a dash.
module Data {
    const COLOR_BATTERY = 0x4FB3FF;
    const COLOR_HEART = 0xFF6B5B;
    const COLOR_STRESS = 0xFFB547;
    const COLOR_FLOORS = 0x3DDC97;

    function value(field) {
        if (field.equals("battery")) {
            return bodyBattery();
        } else if (field.equals("heart")) {
            return heartRate();
        } else if (field.equals("stress")) {
            return stress();
        } else if (field.equals("steps")) {
            var info = ActivityMonitor.getInfo();
            return info.steps;
        } else if (field.equals("floors")) {
            var info = ActivityMonitor.getInfo();
            return info.floorsClimbed;
        }
        return null;
    }

    // How full the ring or bar of this field is: 0.0 to 1.0.
    function fraction(field, v) {
        if (v == null) {
            return 0.0;
        }
        var f = 0.0;
        if (field.equals("battery") || field.equals("stress")) {
            f = v / 100.0;
        } else if (field.equals("heart")) {
            f = v / 120.0;
        } else if (field.equals("steps")) {
            var goal = ActivityMonitor.getInfo().stepGoal;
            f = (goal != null && goal > 0) ? v.toFloat() / goal : 0.0;
        } else if (field.equals("floors")) {
            f = v / 10.0;
        }
        if (f < 0.0) {
            f = 0.0;
        }
        return (f > 0.999) ? 0.999 : f;
    }

    // The colour of a field; steps follow the accent colour of the face.
    function color(field, accent) {
        if (field.equals("battery")) {
            return COLOR_BATTERY;
        } else if (field.equals("heart")) {
            return COLOR_HEART;
        } else if (field.equals("stress")) {
            return COLOR_STRESS;
        } else if (field.equals("floors")) {
            return COLOR_FLOORS;
        }
        return accent;
    }

    function label(field) {
        if (field.equals("battery")) {
            return "BATTERY";
        } else if (field.equals("heart")) {
            return "HEART";
        } else if (field.equals("stress")) {
            return "STRESS";
        } else if (field.equals("steps")) {
            return "STEPS";
        }
        return "FLOORS";
    }

    function text(field) {
        var v = value(field);
        return (v == null) ? "--" : v.format("%d");
    }

    function bodyBattery() {
        if ((Toybox has :SensorHistory) && (Toybox.SensorHistory has :getBodyBatteryHistory)) {
            return newest(SensorHistory.getBodyBatteryHistory({ :period => 1 }));
        }
        return null;
    }

    function stress() {
        if ((Toybox has :SensorHistory) && (Toybox.SensorHistory has :getStressHistory)) {
            return newest(SensorHistory.getStressHistory({ :period => 1 }));
        }
        return null;
    }

    // The latest sample of a sensor history as a whole number, or null.
    function newest(iterator) {
        if (iterator == null) {
            return null;
        }
        var sample = iterator.next();
        if (sample == null || sample.data == null) {
            return null;
        }
        return sample.data.toNumber();
    }

    // The live heart rate; when the watch has none right now, the resting rate from the user profile.
    function heartRate() {
        var activity = Activity.getActivityInfo();
        if (activity != null && activity.currentHeartRate != null) {
            return activity.currentHeartRate;
        }
        var profile = UserProfile.getProfile();
        return (profile != null) ? profile.restingHeartRate : null;
    }
}
