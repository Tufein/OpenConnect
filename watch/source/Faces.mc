import Toybox.Graphics;
import Toybox.System;
import Toybox.Time;
import Toybox.Time.Gregorian;

// The three designs. Sizes are fractions of the screen so they fit a 416 or 454 px round display alike.
module Faces {
    const MUTED = 0x9AA3AF;

    function timeText() {
        var t = System.getClockTime();
        var h = t.hour;
        if (!System.getDeviceSettings().is24Hour) {
            h = h % 12;
            if (h == 0) {
                h = 12;
            }
        }
        return h.format("%02d") + ":" + t.min.format("%02d");
    }

    function dateText() {
        var d = Gregorian.info(Time.now(), Time.FORMAT_MEDIUM);
        return d.day_of_week + " " + d.day + " " + d.month;
    }

    function center(dc, x, y, font, s) {
        dc.drawText(x, y, font, s, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }

    // A colour at a fifth of its brightness, for the unfilled part of a ring.
    function dim(c) {
        var r = ((c >> 16) & 0xFF) / 5;
        var g = ((c >> 8) & 0xFF) / 5;
        var b = (c & 0xFF) / 5;
        return (r << 16) | (g << 8) | b;
    }

    function sleeping(dc) {
        dc.setColor(MUTED, Graphics.COLOR_BLACK);
        dc.clear();
        center(dc, dc.getWidth() / 2, dc.getHeight() / 2, Graphics.FONT_NUMBER_MEDIUM, timeText());
    }

    // The widget: the first field as a ring, all four as rows, and the Body Battery of the last 12 hours as a line.
    function widget(dc) {
        var w = dc.getWidth();
        var h = dc.getHeight();
        var cx = w / 2;
        var fields = Config.FIELDS;
        var main = fields[0];
        var color = Data.color(main, Config.COLOR);
        var pen = w * 3 / 100;
        var radius = w / 2 - pen;

        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();
        dc.setPenWidth(pen);
        dc.setColor(dim(color), Graphics.COLOR_TRANSPARENT);
        dc.drawCircle(cx, h / 2, radius);
        var f = Data.fraction(main, Data.value(main));
        if (f > 0.0) {
            dc.setColor(color, Graphics.COLOR_TRANSPARENT);
            dc.drawArc(cx, h / 2, radius, Graphics.ARC_CLOCKWISE, 90, 90 - (360 * f).toNumber());
        }
        dc.setColor(Config.COLOR, Graphics.COLOR_TRANSPARENT);
        center(dc, cx, h * 13 / 100, Graphics.FONT_XTINY, "OPENCONNECT");
        for (var i = 0; i < 4; i++) {
            var y = h * (28 + 12 * i) / 100;
            dc.setColor(MUTED, Graphics.COLOR_TRANSPARENT);
            dc.drawText(w * 24 / 100, y, Graphics.FONT_XTINY, Data.label(fields[i]), Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);
            dc.setColor(Data.color(fields[i], Config.COLOR), Graphics.COLOR_TRANSPARENT);
            dc.drawText(w * 76 / 100, y, Graphics.FONT_SMALL, Data.text(fields[i]), Graphics.TEXT_JUSTIFY_RIGHT | Graphics.TEXT_JUSTIFY_VCENTER);
        }
        var points = Data.bodyBatteryHistory();
        if (points.size() >= 2) {
            var x0 = w * 26 / 100;
            var x1 = w * 74 / 100;
            var top = h * 78 / 100;
            var bottom = h * 90 / 100;
            dc.setPenWidth(3);
            dc.setColor(Data.COLOR_BATTERY, Graphics.COLOR_TRANSPARENT);
            var px = x0;
            var py = bottom - (bottom - top) * points[0] / 100;
            for (var i = 1; i < points.size(); i++) {
                var x = x0 + (x1 - x0) * i / (points.size() - 1);
                var y = bottom - (bottom - top) * points[i] / 100;
                dc.drawLine(px, py, x, y);
                px = x;
                py = y;
            }
        }
    }

    // Clean Pro: a ring around the big time, the first field is the ring.
    function clean(dc) {
        var w = dc.getWidth();
        var h = dc.getHeight();
        var cx = w / 2;
        var cy = h / 2;
        var fields = Config.FIELDS;
        var main = fields[0];
        var color = Data.color(main, Config.COLOR);
        var pen = w * 4 / 100;
        var radius = w / 2 - pen;

        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();
        dc.setPenWidth(pen);
        dc.setColor(dim(color), Graphics.COLOR_TRANSPARENT);
        dc.drawCircle(cx, cy, radius);
        var f = Data.fraction(main, Data.value(main));
        if (f > 0.0) {
            dc.setColor(color, Graphics.COLOR_TRANSPARENT);
            dc.drawArc(cx, cy, radius, Graphics.ARC_CLOCKWISE, 90, 90 - (360 * f).toNumber());
        }
        dc.setColor(MUTED, Graphics.COLOR_TRANSPARENT);
        center(dc, cx, h * 22 / 100, Graphics.FONT_TINY, dateText());
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        center(dc, cx, cy - h * 3 / 100, Graphics.FONT_NUMBER_HOT, timeText());
        dc.setColor(color, Graphics.COLOR_TRANSPARENT);
        center(dc, cx, h * 62 / 100, Graphics.FONT_SMALL, Data.label(main) + " " + Data.text(main));
        for (var i = 1; i <= 2; i++) {
            var x = (i == 1) ? w * 36 / 100 : w * 64 / 100;
            dc.setColor(Data.color(fields[i], Config.COLOR), Graphics.COLOR_TRANSPARENT);
            center(dc, x, h * 76 / 100, Graphics.FONT_MEDIUM, Data.text(fields[i]));
            dc.setColor(MUTED, Graphics.COLOR_TRANSPARENT);
            center(dc, x, h * 85 / 100, Graphics.FONT_XTINY, Data.label(fields[i]));
        }
    }

    // Dashboard Dark: the time on top, four tiles, a steps bar underneath.
    function dashboard(dc) {
        var w = dc.getWidth();
        var h = dc.getHeight();
        var cx = w / 2;
        var fields = Config.FIELDS;
        var tw = w * 33 / 100;
        var th = h * 25 / 100;
        var gap = w * 2 / 100;

        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();
        center(dc, cx, h * 13 / 100, Graphics.FONT_NUMBER_MEDIUM, timeText());
        for (var i = 0; i < 4; i++) {
            var x = (i % 2 == 0) ? cx - gap / 2 - tw : cx + gap / 2;
            var y = (i < 2) ? h * 27 / 100 : h * 27 / 100 + th + gap;
            dc.setColor(0x12161B, 0x12161B);
            dc.fillRoundedRectangle(x, y, tw, th, w * 4 / 100);
            dc.setColor(Data.color(fields[i], Config.COLOR), Graphics.COLOR_TRANSPARENT);
            center(dc, x + tw / 2, y + th * 28 / 100, Graphics.FONT_XTINY, Data.label(fields[i]));
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            center(dc, x + tw / 2, y + th * 66 / 100, Graphics.FONT_NUMBER_MILD, Data.text(fields[i]));
        }
        var barW = w * 55 / 100;
        var barH = h * 2 / 100;
        var barY = h * 88 / 100;
        dc.setColor(0x22272E, 0x22272E);
        dc.fillRoundedRectangle(cx - barW / 2, barY, barW, barH, barH / 2);
        var steps = Data.fraction("steps", Data.value("steps"));
        if (steps > 0.0) {
            dc.setColor(Config.COLOR, Config.COLOR);
            dc.fillRoundedRectangle(cx - barW / 2, barY, (barW * steps).toNumber(), barH, barH / 2);
        }
    }

    // Retro Pro: a segment bar for the first field and the others as lines, on indigo. The watch has no pixel font,
    // so the arcade feel comes from the blocks and the colours.
    function retro(dc) {
        var w = dc.getWidth();
        var h = dc.getHeight();
        var cx = w / 2;
        var fields = Config.FIELDS;
        var main = fields[0];
        var bw = w * 5 / 100;
        var gap = w * 1 / 100;
        var x0 = cx - (10 * bw + 9 * gap) / 2;
        var lit = (Data.fraction(main, Data.value(main)) * 10 + 0.5).toNumber();

        dc.setColor(Graphics.COLOR_WHITE, 0x15123A);
        dc.clear();
        center(dc, cx, h * 28 / 100, Graphics.FONT_NUMBER_HOT, timeText());
        dc.setColor(0xA9A4D6, Graphics.COLOR_TRANSPARENT);
        center(dc, cx, h * 50 / 100, Graphics.FONT_XTINY, Data.label(main) + " " + Data.text(main));
        for (var i = 0; i < 10; i++) {
            dc.setColor((i < lit) ? Config.COLOR : 0x2E2970, Graphics.COLOR_TRANSPARENT);
            dc.fillRectangle(x0 + i * (bw + gap), h * 56 / 100, bw, h * 6 / 100);
        }
        for (var i = 1; i <= 3; i++) {
            dc.setColor(Data.color(fields[i], Config.COLOR), Graphics.COLOR_TRANSPARENT);
            center(dc, cx, h * (66 + 8 * (i - 1)) / 100, Graphics.FONT_XTINY, Data.label(fields[i]) + " " + Data.text(fields[i]));
        }
    }
}
