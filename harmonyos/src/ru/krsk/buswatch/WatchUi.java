package ru.krsk.buswatch;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the whole watch UI into a UiFrame using a 466x466 @ 320dpi canvas.
 * Screens: route picker, per-route stop board (fisheye), stop menu, settings.
 * All geometry is in dp.
 */
public final class WatchUi {

    private static final int BG_IN = 0xFF12222E;
    private static final int BG_OUT = 0xFF05090D;
    private static final int BORDER = 0xFF1B2C3A;

    private static final int TEXT = 0xFFEAF3FA;
    private static final int DIM = 0xFF8CA3B5;
    private static final int FAINT = 0xFF5D7387;
    private static final int ACCENT = 0xFFFFB020;
    private static final int GOOD = 0xFF3DDC97;
    private static final int WARN = 0xFFFF8A5B;
    private static final int BAD = 0xFFFF6B6B;
    private static final int SEL = 0xFF172835;
    private static final int TRACK = 0xFF1A2A36;
    private static final int BADGE_TEXT = 0xFF0B1218;
    private static final int USER_BG = 0xFF1E3A2A;

    private static final int COLOR_BUS = 0xFFFFB020;
    private static final int COLOR_TROLLEY = 0xFF45B4CE;
    private static final int COLOR_TRAM = 0xFF3DDC97;
    private static final int COLOR_MINIBUS = 0xFFB08CFF;

    private static final float CX = 116.5f;
    private static final float R = 112f;

    private static final float TIME_Y = 24f;
    private static final float TITLE_Y = 49f;
    private static final float ROUTES_TOP = 29.5f;
    private static final float STOPS_TOP = 68f;
    private static final float ROW_W = 168f;
    private static final float MENU_TOP = 76f;
    private static final float INSET = 16f;

    private final float density;
    private final float scaled;
    private final float width;
    private final float height;

    public WatchUi(float density, float scaledDensity, float widthPx, float heightPx) {
        this.density = density;
        this.scaled = scaledDensity;
        this.width = widthPx;
        this.height = heightPx;
    }

    private float dp(float v) {
        return v * density;
    }

    private float sp(float v) {
        return v * scaled;
    }

    public UiFrame build(WatchState s, Metrics m) {
        UiFrame f = new UiFrame((int) width, (int) height);
        // Clip first: the background must be round too, not a square behind it.
        f.clipCircle(dp(CX), dp(CX), dp(R));
        f.radial(dp(CX), dp(96f), dp(300f), BG_IN, BG_OUT);

        // Navigation screens always win over loading/error states: otherwise
        // "back" during a city switch looks broken (the spinner ignores it).
        if (s.screen == WatchState.SCREEN_SETTINGS) {
            drawSettings(f, s, m);
        } else if (s.screen == WatchState.SCREEN_CITIES) {
            drawCitiesScreen(f, s, m);
        } else if (s.screen == WatchState.SCREEN_STOP_MENU) {
            drawStopMenu(f, s, m);
        } else switch (s.status) {
            case WatchState.STATUS_LOADING:
                drawLoading(f, s);
                break;
            case WatchState.STATUS_ERROR:
            case WatchState.STATUS_NO_PERMISSION:
                drawError(f, s);
                break;
            case WatchState.STATUS_EMPTY:
                drawHeader(f, s, Text.upper(s.city == null ? "" : s.city.name), m);
                drawEmpty(f, s);
                break;
            default:
                if (s.screen == WatchState.SCREEN_STOPS) {
                    drawStopsScreen(f, s, m);
                } else {
                    drawRoutesScreen(f, s, m);
                }
                break;
        }

        f.restore();
        f.ring(dp(CX), dp(CX), dp(R), dp(0.8f), BORDER);
        return f;
    }

    // ------------------------------------------------------------------ header

    private void drawHeader(UiFrame f, WatchState s, String title, Metrics m) {
        drawTopBar(f, s, m);
        if (title != null && !title.isEmpty()) {
            f.text(dp(CX), dp(TITLE_Y), sp(9f), ACCENT, UiFrame.ALIGN_CENTER, true, title);
        }
    }

    /** Clock centred on the screen, with a small status dot to its right. */
    private void drawTopBar(UiFrame f, WatchState s, Metrics m) {
        String time = Text.clock(System.currentTimeMillis());
        float timeSize = sp(11.5f);
        f.text(dp(CX), dp(TIME_Y), timeSize, DIM, UiFrame.ALIGN_CENTER, false, time);

        int dotColor = s.manualActive() ? ACCENT
                : ((s.gpsFix || s.hasFix) ? GOOD : FAINT);
        float tw = m != null ? m.width(time, timeSize, false) : dp(30f);
        f.circle(dp(CX) + tw / 2f + dp(6f), dp(TIME_Y - 3.5f), dp(2.6f), dotColor);
    }

    // ------------------------------------------------------------ route picker

    private void drawRoutesScreen(UiFrame f, WatchState s, Metrics m) {
        // no city title: the header would overlap the first list row
        drawHeader(f, s, "", m);

        int top = s.routeSel.topIndex();
        for (int row = 0; row < RouteList.VISIBLE; row++) {
            int idx = top + row;
            RouteInfo r = s.routeAt(idx);
            if (r == null) continue;

            float scale = RouteList.scale(row);
            float cyDp = RouteList.rowCenterDp(ROUTES_TOP, row);
            float cy = dp(cyDp);
            float rowH = dp(RouteList.rowHeightDp(row));
            float rowWDp = FocalList.widthForRow(RouteList.rowTopDp(ROUTES_TOP, row),
                    RouteList.rowHeightDp(row), R, CX, 10f, 214f);
            float halfW = dp(rowWDp) / 2f;
            boolean selected = idx == s.routeSel.selectedIndex();

            if (selected) {
                f.roundRect(dp(CX) - halfW, cy - rowH / 2f + dp(1.5f),
                        halfW * 2f, rowH - dp(3f), (rowH - dp(3f)) / 2f, SEL);
            }

            // route number badge (left)
            float badgeX = dp(CX) - halfW + dp(INSET + 6f);
            int color = routeColor(r.type);
            float badgeR = dp(17f) * scale;
            f.circle(badgeX, cy, badgeR, color);
            f.circle(badgeX, cy, badgeR - dp(1f) * scale, color);
            String num = r.name;
            float numBase = num.length() >= 4 ? sp(9.5f) : (num.length() == 3 ? sp(10.5f) : sp(12.5f));
            float numSize = numBase * scale;
            f.text(badgeX, cy + numSize * 0.36f, numSize, BADGE_TEXT, UiFrame.ALIGN_CENTER, true, num);

            // two text lines: route title on top, vehicle type underneath
            float textX = badgeX + badgeR + dp(8f);
            float textW = (dp(CX) + halfW - dp(11f)) - textX;
            float titleSize = sp(13f) * scale;
            String title = r.title.isEmpty() ? typeLabel(r.type) : r.title;
            title = ellipsize(title, titleSize, false, textW, m);

            f.text(textX, cy - dp(1.5f) * scale, titleSize,
                    selected ? TEXT : 0xFFD3DFE9, UiFrame.ALIGN_LEFT, false, title);
            f.text(textX, cy + dp(12f) * scale, sp(8.5f) * scale,
                    selected ? DIM : FAINT, UiFrame.ALIGN_LEFT, false, typeLabel(r.type));
        }

        drawScrollbar(f, s.routes.size(), s.routeSel.topIndex());
    }

    // -------------------------------------------------------------- stop board

    private void drawStopsScreen(UiFrame f, WatchState s, Metrics m) {
        RouteInfo route = s.selectedRoute;

        drawTopBar(f, s, m);

        // One wide control split in two equal halves: [ ‹ ] [ ⇄ destination ].
        // The back half is exactly as wide as the direction label half.
        float ctrlTop = dp(31f);
        float ctrlH = dp(30f);
        float ctrlHalfW = dp(70f);
        f.roundRect(dp(CX) - ctrlHalfW, ctrlTop, ctrlHalfW * 2f, ctrlH, ctrlH / 2f, 0xFF16242F);
        f.line(dp(CX), ctrlTop + dp(5f), dp(CX), ctrlTop + ctrlH - dp(5f), dp(0.8f), 0xFF2A4256);

        // left: back (chevron + label, same half-width as the direction side)
        f.text(dp(CX - 58f), dp(31f) + dp(20f), sp(16f), TEXT, UiFrame.ALIGN_CENTER, false, "‹");
        f.text(dp(CX - 48f), dp(31f) + dp(18.5f), sp(10f), TEXT, UiFrame.ALIGN_LEFT, false, "назад");

        // right: direction toggle
        String dest = destination(route, s.dir);
        boolean canToggle = s.canToggleDirection();
        f.text(dp(CX + 9f), dp(31f) + dp(18.5f), sp(10.5f), canToggle ? ACCENT : FAINT,
                UiFrame.ALIGN_LEFT, true, "⇄");
        String destShort = ellipsize(dest, sp(9.5f), true, dp(48f), m);
        f.text(dp(CX + 22f), dp(31f) + dp(18.5f), sp(9.5f), TEXT, UiFrame.ALIGN_LEFT, true, destShort);

        // stop rows (fisheye)
        if (s.board == null) {
            // first load after choosing a route: show progress instead of a blank area
            float cy = dp(116f);
            f.ring(dp(CX), cy, dp(16f), dp(2.2f), 0xFF1A2A36);
            f.arc(dp(CX), cy, dp(16f), (System.currentTimeMillis() / 4) % 360, 96, dp(2.4f), ACCENT);
            f.text(dp(CX), dp(154f), sp(9.5f), DIM, UiFrame.ALIGN_CENTER, false, "Загрузка…");
        } else {
            int top = s.stopSel.topIndex();
            for (int row = 0; row < FocalList.VISIBLE; row++) {
                Board.Row br = rowAt(s, top + row);
                if (br == null) continue;
                drawStopRow(f, s, m, br, row, top + row == s.stopSel.selectedIndex());
            }
            drawScrollbar(f, s.board.rows.size(), s.stopSel.topIndex());
        }
    }

    private void drawStopRow(UiFrame f, WatchState s, Metrics m, Board.Row br,
                             int row, boolean selected) {
        float scale = FocalList.scale(row);
        float cyDp = FocalList.rowCenterDp(STOPS_TOP, row);
        float cy = dp(cyDp);
        float rowH = dp(FocalList.rowHeightDp(row));
        float rowWDp = FocalList.widthForRow(FocalList.rowTopDp(STOPS_TOP, row),
                FocalList.rowHeightDp(row), R, CX, 10f, 214f);
        float halfW = dp(rowWDp) / 2f;

        if (selected) {
            f.roundRect(dp(CX) - halfW, cy - rowH / 2f + dp(0.5f),
                    halfW * 2f, rowH - dp(1f), (rowH - dp(1f)) / 2f, SEL);
        }
        if (br.isUserStop) {
            f.roundRect(dp(CX) - halfW, cy - rowH / 2f + dp(0.5f),
                    halfW * 2f, rowH - dp(1f), (rowH - dp(1f)) / 2f, USER_BG);
        }

        // left: index / user marker / alert ring
        float leftX = dp(CX) - halfW + dp(INSET + 4f);
        if (br.isUserStop) {
            float mr = dp(11.5f) * scale;
            f.circle(leftX, cy, mr, GOOD);
            float markSize = sp(9f) * scale;
            f.text(leftX, cy + markSize * 0.36f, markSize, BADGE_TEXT,
                    UiFrame.ALIGN_CENTER, true, "вы");
        } else {
            float idxSize = sp(9.5f) * scale;
            f.text(leftX, cy + idxSize * 0.36f, idxSize, FAINT, UiFrame.ALIGN_CENTER, false,
                    String.valueOf(br.index + 1));
        }
        if (s.isAlertArmedAt(br.index)) {
            f.ring(leftX, cy, dp(14f) * scale, dp(1.5f), ACCENT);
        }

        // middle: stop name, optically centred on the row
        float nameX = dp(CX) - halfW + dp(INSET + 19f);
        float nameW = (dp(CX) + halfW - dp(INSET + 26f)) - nameX;
        float nameSize = sp(13.5f) * scale;
        String name = ellipsize(br.stop.name, nameSize, false, nameW, m);
        f.text(nameX, cy + nameSize * 0.36f, nameSize,
                selected || br.isUserStop ? TEXT : 0xFFD3DFE9, UiFrame.ALIGN_LEFT, false, name);

        // right: bus here / eta
        float rightX = dp(CX) + halfW - dp(INSET + 6f);
        if (br.hasBusHere()) {
            // one dot per bus at the stop, in a horizontal row, no text
            int n = Math.min(br.atStop.size(), 3);
            float r = dp(4.2f) * scale;
            float gap = dp(3.2f) * scale;
            float total = n * 2f * r + (n - 1) * gap;
            float x = rightX - total / 2f + r;
            for (int i = 0; i < n; i++) {
                f.circle(x + i * (2f * r + gap), cy, r, GOOD);
            }
        } else if (br.etaMinutes > 0) {
            float etaSize = sp(12.5f) * scale;
            f.text(rightX, cy + etaSize * 0.36f, etaSize, ACCENT, UiFrame.ALIGN_CENTER, true,
                    "~" + br.etaMinutes + "′");
        } else {
            float dashSize = sp(9f) * scale;
            f.text(rightX, cy + dashSize * 0.36f, dashSize, FAINT, UiFrame.ALIGN_CENTER, false, "—");
        }
    }

    // ------------------------------------------------------------ stop menu

    private void drawStopMenu(UiFrame f, WatchState s, Metrics m) {
        Board.Row br = s.selectedStopRow();
        String stopName = br == null ? "" : br.stop.name;
        boolean isManualHere = br != null && s.manualActive()
                && Geo.distanceMeters(s.manualLat, s.manualLon, br.stop.lat, br.stop.lon) < 60.0;
        boolean alertHere = br != null && s.isAlertArmedAt(br.index);

        f.text(dp(CX), dp(TIME_Y), sp(12f), DIM, UiFrame.ALIGN_CENTER, false,
                Text.clock(System.currentTimeMillis()));
        f.text(dp(CX), dp(TITLE_Y), sp(10f), ACCENT, UiFrame.ALIGN_CENTER, true, "ОСТАНОВКА");
        String sn = ellipsize(stopName, sp(12f), true, dp(130f), m);
        f.text(dp(CX), dp(67f), sp(12f), TEXT, UiFrame.ALIGN_CENTER, true, sn);

        drawMenuItem(f, 0, s.menuSel.selectedIndex() == 0,
                isManualHere ? "✓ Я здесь (убрать)" : "Я здесь", 40f, 36f, 13.5f, m);
        drawMenuItem(f, 1, s.menuSel.selectedIndex() == 1,
                alertHere ? "✓ Жду (снять)" : "Ждать автобус", 40f, 36f, 13.5f, m);
        drawMenuItem(f, 2, s.menuSel.selectedIndex() == 2, "Назад", 40f, 36f, 13.5f, m);
    }

    // ------------------------------------------------------------ city picker

    private void drawCitiesScreen(UiFrame f, WatchState s, Metrics m) {
        drawTopBar(f, s, m);
        int total = s.cityOptionsCount();
        int top = s.citySel.topIndex();
        for (int row = 0; row < RouteList.VISIBLE; row++) {
            int idx = top + row;
            if (idx < 0 || idx >= total) continue;
            float scale = RouteList.scale(row);
            float cy = dp(RouteList.rowCenterDp(ROUTES_TOP, row));
            float rowH = dp(RouteList.rowHeightDp(row));
            float rowWDp = FocalList.widthForRow(RouteList.rowTopDp(ROUTES_TOP, row),
                    RouteList.rowHeightDp(row), R, CX, 10f, 214f);
            float halfW = dp(rowWDp) / 2f;
            boolean selected = idx == s.citySel.selectedIndex();

            if (selected) {
                f.roundRect(dp(CX) - halfW, cy - rowH / 2f + dp(1f),
                        halfW * 2f, rowH - dp(2f), (rowH - dp(2f)) / 2f, SEL);
            }

            String name = s.cityOptionName(idx);
            float size = sp(13f) * scale;
            name = ellipsize(name, size, idx == 0, halfW * 2f - dp(34f), m);
            f.text(dp(CX), cy + size * 0.36f, size,
                    selected ? TEXT : 0xFFD3DFE9, UiFrame.ALIGN_CENTER, idx == 0, name);

            if (!s.cityAuto && s.city != null && idx > 0
                    && s.city.name.equals(s.cityOptionName(idx))) {
                f.circle(dp(CX) + halfW - dp(18f), cy, dp(4f), GOOD);
            }
        }
        drawScrollbar(f, total, s.citySel.topIndex());
    }

    // ---------------------------------------------------------------- settings

    private void drawSettings(UiFrame f, WatchState s, Metrics m) {
        f.text(dp(CX), dp(TIME_Y), sp(12f), DIM, UiFrame.ALIGN_CENTER, false,
                Text.clock(System.currentTimeMillis()));
        f.text(dp(CX), dp(TITLE_Y), sp(10f), ACCENT, UiFrame.ALIGN_CENTER, true, "НАСТРОЙКИ");
        // status line instead of a separate menu item (no duplicate entries)
        String pos = s.manualActive() ? ("позиция: " + ellipsize(s.manualStopName, sp(8.5f), false, dp(80f), m))
                : "позиция: авто";
        f.text(dp(CX), dp(66f), sp(8.5f), FAINT, UiFrame.ALIGN_CENTER, false, pos);

        drawMenuItem(f, 0, s.menuSel.selectedIndex() == 0,
                "Город: " + (s.city == null ? "—" : s.city.name), 34f, 28f, 11.5f, m);
        drawMenuItem(f, 1, s.menuSel.selectedIndex() == 1,
                "Автопозиция: " + (s.autoTrack ? "ВКЛ" : "ВЫКЛ"), 34f, 28f, 11.5f, m);
        drawMenuItem(f, 2, s.menuSel.selectedIndex() == 2,
                "Ожидание: " + (s.alertsEnabled ? "ВКЛ" : "ВЫКЛ"), 34f, 28f, 11.5f, m);
        drawMenuItem(f, 3, s.menuSel.selectedIndex() == 3, "Назад", 34f, 28f, 11.5f, m);
    }

    private void drawMenuItem(UiFrame f, int index, boolean selected, String label, Metrics m) {
        drawMenuItem(f, index, selected, label, 34f, 28f, 11.5f, m);
    }

    /** Menu row whose highlight follows the round display instead of a fixed width. */
    private void drawMenuItem(UiFrame f, int index, boolean selected, String label,
                             float stepDp, float hDp, float fontSp, Metrics m) {
        float topDp = MENU_TOP + stepDp * index;
        float top = dp(topDp);
        float h = dp(hDp);
        float cy = top + h / 2f;
        float widthDp = FocalList.widthForRow(topDp + 1f, hDp - 2f, R, CX, 10f, 214f);
        float halfW = dp(widthDp) / 2f;
        if (selected) {
            f.roundRect(dp(CX) - halfW, top + dp(1f), halfW * 2f, h - dp(2f),
                    (h - dp(2f)) / 2f, SEL);
        }
        float fontPx = sp(fontSp);
        String text = ellipsize(label, fontPx, true, halfW * 2f - dp(18f), m);
        f.text(dp(CX), cy + fontPx * 0.36f, fontPx, selected ? TEXT : 0xFFD3DFE9,
                UiFrame.ALIGN_CENTER, true, text);
    }

    // ----------------------------------------------------------------- helpers

    private static Board.Row rowAt(WatchState s, int index) {
        if (s.board == null || index < 0 || index >= s.board.rows.size()) return null;
        return s.board.rows.get(index);
    }

    private static String destination(RouteInfo route, int dir) {
        String[] parts = route.title.trim().split("\\s*(?:↔|⇄|—|–|-)+\\s*");
        if (parts.length >= 2) {
            String a = parts[0].trim();
            String b = parts[parts.length - 1].trim();
            return dir == 0 ? b : a;
        }
        return route.title;
    }

    private void drawScrollbar(UiFrame f, int n, int topIndex) {
        if (n <= FocalList.VISIBLE) return;
        float r = dp(108f);
        float cx = dp(CX);
        float cy = dp(CX);
        float trackStart = -52f;
        float trackSweep = 104f;
        f.arc(cx, cy, r, trackStart, trackSweep, dp(1.4f), TRACK);

        int lo = FocalList.minTop(n);
        int hi = FocalList.maxTop(n);
        float frac = Geo.clamp((topIndex - lo) / (float) Math.max(1, hi - lo), 0f, 1f);
        float thumb = Math.max(14f, trackSweep * FocalList.VISIBLE / n);
        float start = trackStart + (trackSweep - thumb) * frac;
        f.arc(cx, cy, r, start, thumb, dp(2.2f), ACCENT);
    }

    // ------------------------------------------------------------- status screens

    private void drawLoading(UiFrame f, WatchState s) {
        float cy = dp(98f);
        long t = System.currentTimeMillis();
        f.ring(dp(CX), cy, dp(21f), dp(2.4f), 0xFF1A2A36);
        f.arc(dp(CX), cy, dp(21f), (t / 4) % 360, 96, dp(2.6f), ACCENT);
        f.text(dp(CX), dp(146f), sp(12f), TEXT, UiFrame.ALIGN_CENTER, true, "Загрузка маршрутов");
        f.text(dp(CX), dp(164f), sp(8.5f), DIM, UiFrame.ALIGN_CENTER, false, "Красноярск · ГЛОНАСС");
        if (s.gpsFix || s.hasFix) {
            f.text(dp(CX), dp(180f), sp(7.5f), GOOD, UiFrame.ALIGN_CENTER, false,
                    "GPS ±" + Math.max(0, s.accuracyM) + " м");
        }
    }

    private void drawEmpty(UiFrame f, WatchState s) {
        f.text(dp(CX), dp(108f), sp(14f), TEXT, UiFrame.ALIGN_CENTER, true, "Маршрутов нет");
        f.text(dp(CX), dp(128f), sp(8.5f), DIM, UiFrame.ALIGN_CENTER, false,
                "не удалось загрузить каталог");
        f.text(dp(CX), dp(170f), sp(8f), ACCENT, UiFrame.ALIGN_CENTER, false, "ОБНОВИТЬ");
    }

    private void drawError(UiFrame f, WatchState s) {
        boolean denied = s.status == WatchState.STATUS_NO_PERMISSION;
        int color = denied ? WARN : BAD;

        // значок поднят выше, чтобы не наезжать на текст сообщения
        f.circle(dp(CX), dp(72f), dp(17f), 0x22101010 | (color & 0x00FFFFFF));
        f.ring(dp(CX), dp(72f), dp(17f), dp(1.4f), color);
        f.text(dp(CX), dp(79f), sp(16f), color, UiFrame.ALIGN_CENTER, true, "!");

        String msg = s.message == null || s.message.isEmpty() ? "Нет данных" : s.message;
        List<String> lines = wrap(msg, sp(13f), true, dp(136f), 2, null);
        for (int i = 0; i < lines.size(); i++) {
            f.text(dp(CX), dp(106f) + i * dp(17f), sp(13f), TEXT,
                    UiFrame.ALIGN_CENTER, true, lines.get(i));
        }

        // почему пусто: ночью общественный транспорт не ходит
        boolean night = Text.isNight();
        String nightLine = denied ? "разрешите доступ к геопозиции"
                : (night ? "Сейчас ночь — автобусы не ходят" : "Ночью автобусы не ходят");
        f.text(dp(CX), dp(142f), sp(9f),
                denied ? FAINT : (night ? WARN : 0xFF9FB3C4),
                UiFrame.ALIGN_CENTER, false, nightLine);

        // two wide buttons: back (left) and retry (right)
        drawErrorButton(f, dp(CX - 41f), "НАЗАД", false);
        drawErrorButton(f, dp(CX + 41f), "ПОВТОР", true);
    }

    /** Wide pill button used on the error screen (74×32 dp). */
    private void drawErrorButton(UiFrame f, float cx, String label, boolean accent) {
        float w = dp(74f);
        float h = dp(32f);
        float cy = dp(180f);
        f.roundRect(cx - w / 2f, cy - h / 2f, w, h, h / 2f,
                accent ? 0xFF2A2114 : 0xFF172835);
        f.text(cx, cy + sp(10.5f) * 0.36f, sp(10.5f), accent ? ACCENT : TEXT,
                UiFrame.ALIGN_CENTER, true, label);
    }

    private String typeLabel(int type) {
        switch (type) {
            case 1:
                return "Троллейбус";
            case 2:
                return "Трамвай";
            case 3:
                return "Маршрутка";
            default:
                return "Автобус";
        }
    }

    private int routeColor(int type) {
        switch (type) {
            case 1:
                return COLOR_TROLLEY;
            case 2:
                return COLOR_TRAM;
            case 3:
                return COLOR_MINIBUS;
            default:
                return COLOR_BUS;
        }
    }

    private static String ellipsize(String text, float sizePx, boolean bold, float maxPx, Metrics m) {
        if (text == null) return "";
        if (m == null) return Text.ellipsize(text, 18);
        if (m.width(text, sizePx, bold) <= maxPx) return text;
        String ell = "…";
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            String candidate = text.substring(0, mid).trim() + ell;
            if (m.width(candidate, sizePx, bold) <= maxPx) lo = mid;
            else hi = mid - 1;
        }
        return text.substring(0, Math.max(0, lo)).trim() + ell;
    }

    private static List<String> wrap(String text, float sizePx, boolean bold, float maxPx,
                                     int maxLines, Metrics m) {
        List<String> out = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            out.add("");
            return out;
        }
        String[] words = text.trim().split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            boolean fits = m == null ? candidate.length() <= 18 : m.width(candidate, sizePx, bold) <= maxPx;
            if (fits) {
                line.setLength(0);
                line.append(candidate);
            } else {
                if (line.length() > 0) out.add(line.toString());
                line.setLength(0);
                line.append(word);
                if (out.size() == maxLines - 1) break;
            }
        }
        if (out.size() < maxLines) out.add(line.toString());
        if (out.size() > maxLines) out = new ArrayList<>(out.subList(0, maxLines));
        if (!out.isEmpty()) {
            int last = out.size() - 1;
            String lastLine = out.get(last);
            if (m == null ? lastLine.length() > 18 : m.width(lastLine, sizePx, bold) > maxPx) {
                out.set(last, ellipsize(lastLine, sizePx, bold, maxPx, m));
            }
        }
        return out;
    }
}
