package ru.krsk.buswatch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.View;

/** Round watch surface: renders a UiFrame and owns crown/touch navigation. */
public final class WatchView extends View implements Metrics {

    public interface Listener {
        void onRouteChosen(RouteInfo route);

        void onToggleDirection();

        void onBack();

        void onRetry();

        void onOpenSettings();

        void onOpenStopMenu();

        void onMenuActivate(int index);

        void onCityChosen(int index);
    }

    private static final float DESIGN = Displays.DESIGN;
    private static final float ROUTES_TOP_DP = 29.5f;
    private static final float STOPS_TOP_DP = 68f;
    private static final float LONG_PRESS_MS = 500f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private final Path clip = new Path();
    private final RectF oval = new RectF();
    private final float density;

    private final WatchUi ui;
    private final Rotary rotary = new Rotary();
    private WatchState state;
    private Listener listener;

    private float downX, downY, lastY, dragAccum;
    private long downTime;
    private boolean dragging;
    private long lastBackHandledAt;
    private VelocityTracker velocityTracker;
    private final Handler inertiaHandler = new Handler(Looper.getMainLooper());
    private Inertia inertia;
    private long inertiaLastMs;


    public WatchView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        float scaled = context.getResources().getDisplayMetrics().scaledDensity;
        ui = new WatchUi(density, scaled, DESIGN, DESIGN);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setContentDescription("Автобусы Красноярска");
    }

    public void setState(WatchState state) {
        this.state = state;
        invalidate();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public float width(String s, float sizePx, boolean isBold) {
        textPaint.setTypeface(isBold ? bold : regular);
        textPaint.setTextSize(sizePx);
        return textPaint.measureText(s);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (state == null) return;

        float scale = Displays.scaleFor(getWidth(), getHeight());
        canvas.save();
        canvas.translate((getWidth() - DESIGN * scale) / 2f, (getHeight() - DESIGN * scale) / 2f);
        canvas.scale(scale, scale);

        UiFrame frame = ui.build(state, this);
        replay(canvas, frame);

        canvas.restore();

        if (state.status == WatchState.STATUS_LOADING || state.refreshing) {
            postInvalidateDelayed(80);
        }
    }

    private void replay(Canvas canvas, UiFrame frame) {
        for (UiFrame.Op op : frame.ops) {
            switch (op.type) {
                case UiFrame.RECT:
                    fill.setColor(op.color);
                    canvas.drawRect(op.x, op.y, op.x + op.w, op.y + op.h, fill);
                    break;
                case UiFrame.CIRCLE:
                    fill.setColor(op.color);
                    fill.setShader(null);
                    canvas.drawCircle(op.x, op.y, op.w, fill);
                    break;
                case UiFrame.ROUND_RECT:
                    fill.setColor(op.color);
                    fill.setShader(null);
                    canvas.drawRoundRect(op.x, op.y, op.x + op.w, op.y + op.h, op.r, op.r, fill);
                    break;
                case UiFrame.RING:
                    stroke.setColor(op.color);
                    stroke.setStyle(Paint.Style.STROKE);
                    stroke.setStrokeWidth(op.stroke);
                    stroke.setShader(null);
                    canvas.drawCircle(op.x, op.y, op.w, stroke);
                    break;
                case UiFrame.ARC:
                    stroke.setColor(op.color);
                    stroke.setStyle(Paint.Style.STROKE);
                    stroke.setStrokeWidth(op.stroke);
                    stroke.setStrokeCap(Paint.Cap.ROUND);
                    stroke.setShader(null);
                    oval.set(op.x - op.w, op.y - op.w, op.x + op.w, op.y + op.w);
                    canvas.drawArc(oval, op.start, op.sweep, false, stroke);
                    break;
                case UiFrame.LINE:
                    stroke.setColor(op.color);
                    stroke.setStyle(Paint.Style.STROKE);
                    stroke.setStrokeWidth(op.stroke);
                    stroke.setShader(null);
                    canvas.drawLine(op.x, op.y, op.x2, op.y2, stroke);
                    break;
                case UiFrame.RADIAL:
                    fill.setShader(new RadialGradient(op.x, op.y, op.w, op.color, op.color2,
                            Shader.TileMode.CLAMP));
                    canvas.drawPaint(fill);
                    fill.setShader(null);
                    break;
                case UiFrame.TEXT:
                    textPaint.setColor(op.color);
                    textPaint.setTypeface(op.bold ? bold : regular);
                    textPaint.setTextSize(op.size);
                    textPaint.setTextAlign(op.align == UiFrame.ALIGN_CENTER
                            ? Paint.Align.CENTER
                            : (op.align == UiFrame.ALIGN_RIGHT ? Paint.Align.RIGHT : Paint.Align.LEFT));
                    canvas.drawText(op.text, op.x, op.y, textPaint);
                    break;
                case UiFrame.CLIP_CIRCLE:
                    clip.reset();
                    clip.addCircle(op.x, op.y, op.w, Path.Direction.CW);
                    canvas.save();
                    canvas.clipPath(clip);
                    break;
                case UiFrame.RESTORE:
                    canvas.restore();
                    break;
                default:
                    break;
            }
        }
    }

    // ---------------------------------------------------------------- input

    private Selection activeSelection() {
        if (state == null) return null;
        switch (state.screen) {
            case WatchState.SCREEN_STOPS:
                return state.stopSel;
            case WatchState.SCREEN_STOP_MENU:
            case WatchState.SCREEN_SETTINGS:
                return state.menuSel;
            case WatchState.SCREEN_CITIES:
                return state.citySel;
            default:
                return state.routeSel;
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event == null) return false;
        int source = event.getSource();
        boolean fromEncoder = (source & InputDevice.SOURCE_ROTARY_ENCODER) == InputDevice.SOURCE_ROTARY_ENCODER;
        if (fromEncoder || event.getAction() == MotionEvent.ACTION_SCROLL) {
            float delta = event.getAxisValue(MotionEvent.AXIS_SCROLL);
            if (delta == 0f) delta = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
            if (delta == 0f) delta = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (delta != 0f) {
                int steps = rotary.onRotate(delta);
                if (steps != 0) moveSelection(steps);
                return true;
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_VOLUME_UP:
                moveSelection(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                moveSelection(1);
                return true;
            // the watch's lower hardware button (and MENU) opens the context
            // menu: settings on the route list, stop actions on the board
            case KeyEvent.KEYCODE_STEM_1:
            case KeyEvent.KEYCODE_STEM_2:
            case KeyEvent.KEYCODE_STEM_3:
            case KeyEvent.KEYCODE_MENU:
                showContextMenuFromKey();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (state != null && state.screen == WatchState.SCREEN_STOPS) {
                    toggleDirection();
                    return true;
                }
                return true; // swallow horizontal dpad elsewhere (gesture lock)
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_STEM_PRIMARY:
            case KeyEvent.KEYCODE_BUTTON_A:
                activate();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (state != null && state.screen != WatchState.SCREEN_ROUTES) {
                    back();
                    return true;
                }
                return super.onKeyDown(keyCode, event);
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    /** Smooth deceleration after a flick. */
    private final Runnable inertiaTick = new Runnable() {
        @Override
        public void run() {
            if (inertia == null) return;
            long now = SystemClock.uptimeMillis();
            float dt = inertiaLastMs == 0 ? 0.016f : Math.min(0.05f, (now - inertiaLastMs) / 1000f);
            inertiaLastMs = now;
            int steps = inertia.update(dt);
            if (steps != 0) {
                Selection sel = activeSelection();
                if (sel != null && sel.move(steps)) invalidate();
            }
            if (inertia != null && inertia.active()) {
                inertiaHandler.postDelayed(this, 16);
            } else {
                inertia = null;
            }
        }
    };

    private void startInertia(float rowsPerSecond) {
        cancelInertia();
        if (Math.abs(rowsPerSecond) < 1.5f) return;
        inertia = new Inertia(rowsPerSecond);
        inertiaLastMs = 0;
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        inertiaHandler.post(inertiaTick);
    }

    private void cancelInertia() {
        inertia = null;
        inertiaHandler.removeCallbacks(inertiaTick);
    }

    private void moveSelection(int steps) {
        moveSelection(steps, true);
    }

    private void moveSelection(int steps, boolean haptic) {
        if (state == null || steps == 0) return;
        Selection sel = activeSelection();
        if (sel == null) return;
        if (sel.move(steps)) {
            if (haptic) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            invalidate();
        }
    }

    private void activate() {
        if (state == null) return;
        cancelInertia();
        if (state.status == WatchState.STATUS_ERROR
                || state.status == WatchState.STATUS_NO_PERMISSION
                || state.status == WatchState.STATUS_EMPTY) {
            retry();
            return;
        }
        switch (state.screen) {
            case WatchState.SCREEN_STOPS:
                back();
                return;
            case WatchState.SCREEN_STOP_MENU:
            case WatchState.SCREEN_SETTINGS:
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (listener != null) listener.onMenuActivate(state.menuSel.selectedIndex());
                return;
            case WatchState.SCREEN_CITIES:
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (listener != null) listener.onCityChosen(state.citySel.selectedIndex());
                return;
            default:
                RouteInfo r = state.routeAt(state.routeSel.selectedIndex());
                if (r != null) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    if (listener != null) listener.onRouteChosen(r);
                }
        }
    }

    /** Opens settings (route list) or the stop actions (stop board). */
    public void showContextMenuFromKey() {
        if (state == null) return;
        if (state.status == WatchState.STATUS_ERROR
                || state.status == WatchState.STATUS_NO_PERMISSION
                || state.status == WatchState.STATUS_EMPTY) {
            return;
        }
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if (state.screen == WatchState.SCREEN_ROUTES) {
            if (listener != null) listener.onOpenSettings();
        } else if (state.screen == WatchState.SCREEN_STOPS) {
            if (state.selectedStopRow() != null && listener != null) {
                listener.onOpenStopMenu();
            }
        } else {
            back();
        }
    }

    private void toggleDirection() {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if (listener != null) listener.onToggleDirection();
    }

    private void back() {
        if (listener != null) listener.onBack();
    }

    /**
     * Back initiated by a touch gesture. Only these can be echoed by the system
     * back gesture, so only they arm the duplicate-suppression window; keyboard
     * and crown presses must always work, even in quick succession.
     */
    private void backFromGesture() {
        lastBackHandledAt = SystemClock.uptimeMillis();
        back();
    }

    /** True right after an in-app back, so the Activity can ignore the echo. */
    public boolean recentlyHandledBack() {
        return SystemClock.uptimeMillis() - lastBackHandledAt < 700L;
    }

    /**
     * Keep edge swipes for the app: otherwise the system back gesture steals
     * the swipe and can pop the whole activity.
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            int edge = Math.max(1, (int) dp(30f));
            java.util.List<android.graphics.Rect> rects = new java.util.ArrayList<>(2);
            rects.add(new android.graphics.Rect(0, 0, Math.min(edge, getWidth()), getHeight()));
            rects.add(new android.graphics.Rect(Math.max(0, getWidth() - edge), 0,
                    getWidth(), getHeight()));
            setSystemGestureExclusionRects(rects);
        }
    }

    private void retry() {
        if (listener != null) listener.onRetry();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (state == null) return false;
        float scale = Displays.scaleFor(getWidth(), getHeight());
        float offX = (getWidth() - DESIGN * scale) / 2f;
        float offY = (getHeight() - DESIGN * scale) / 2f;
        float designX = (e.getX() - offX) / scale;
        float designY = (e.getY() - offY) / scale;

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = designX;
                downY = designY;
                lastY = designY;
                dragAccum = 0;
                dragging = false;
                downTime = SystemClock.uptimeMillis();
                cancelInertia();
                if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
                else velocityTracker.clear();
                velocityTracker.addMovement(e);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (velocityTracker != null) velocityTracker.addMovement(e);
                float dy = designY - lastY;
                lastY = designY;
                float movedX = Math.abs(designX - downX);
                float movedY = Math.abs(designY - downY);
                if (movedY > dp(10f) || movedX > dp(10f)) dragging = true;
                // Only a predominantly vertical drag scrolls; horizontal motion
                // is reserved for the back gesture (gesture lock).
                boolean vertical = movedY > movedX * 1.2f;
                Selection sel = activeSelection();
                if (vertical && sel != null && sel.size() > 0) {
                    dragAccum += dy;
                    while (dragAccum <= -dp(26f)) {
                        dragAccum += dp(26f);
                        moveSelection(1);
                    }
                    while (dragAccum >= dp(26f)) {
                        dragAccum -= dp(26f);
                        moveSelection(-1);
                    }
                } else if (!vertical) {
                    dragAccum = 0f;
                }
                return true;
            case MotionEvent.ACTION_UP: {
                long dur = SystemClock.uptimeMillis() - downTime;
                float totalX = designX - downX;
                float totalY = designY - downY;
                float dist = (float) Math.hypot(totalX, totalY);

                // Back = swipe to the RIGHT (plus a forgiving left-edge swipe).
                boolean horizontal = totalX > dp(24f) && totalX > Math.abs(totalY) * 1.25f;
                boolean edgeSwipe = downX < dp(80f) && totalX > dp(18f)
                        && totalX > Math.abs(totalY) * 0.9f;
                if (state.screen != WatchState.SCREEN_ROUTES && (horizontal || edgeSwipe)) {
                    backFromGesture();
                    return true;
                }

                // vertical flick: keep scrolling with decaying speed
                if (Math.abs(totalY) > Math.abs(totalX) * 1.2f && dist > dp(16f)) {
                    startInertia(velocityRowsPerSecond(e));
                    return true;
                }

                // A press that barely moved is always a tap, even if small
                // finger jitter set the "dragging" flag along the way.
                if (dist < dp(12f)) {
                    if (dur >= LONG_PRESS_MS) {
                        handleLongPress(designX, designY);
                    } else {
                        handleTap(designX, designY);
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                recycleTracker();
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    private void handleLongPress(float x, float y) {
        if (state.status == WatchState.STATUS_ERROR
                || state.status == WatchState.STATUS_NO_PERMISSION
                || state.status == WatchState.STATUS_EMPTY) {
            return;
        }
        if (state.screen == WatchState.SCREEN_ROUTES) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (listener != null) listener.onOpenSettings();
        } else if (state.screen == WatchState.SCREEN_STOPS) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (listener != null) listener.onOpenStopMenu();
        } else {
            // menus: a slow press still activates the item it landed on
            handleTap(x, y);
        }
    }

    private void handleTap(float x, float y) {
        cancelInertia();
        if (state.status == WatchState.STATUS_ERROR
                || state.status == WatchState.STATUS_NO_PERMISSION
                || state.status == WatchState.STATUS_EMPTY) {
            float yDp = y / density;
            float xDp = x / density;
            // two buttons on the error screen: [НАЗАД] [ПОВТОРИТЬ]
            if (yDp >= 160f && yDp <= 200f) {
                if (xDp < 114.5f) {
                    back();          // "НАЗАД"  (CX-42 ± 38)
                    return;
                }
                if (xDp > 118.5f) {
                    retry();         // "ПОВТОР"  (CX+42 ± 38)
                    return;
                }
            }
            retry();
            return;
        }
        switch (state.screen) {
            case WatchState.SCREEN_STOPS:
                tapStops(x, y);
                return;
            case WatchState.SCREEN_CITIES:
                tapCities(y);
                return;
            case WatchState.SCREEN_STOP_MENU:
            case WatchState.SCREEN_SETTINGS:
                tapMenu(y);
                return;
            default:
                tapRoutes(x, y);
        }
    }

    private void tapStops(float x, float y) {
        float yDp = y / density;
        float xDp = x / density;
        // segmented control [ back | direction ], y 31..61 dp, CX ± 70
        if (yDp >= 28f && yDp <= 64f && Math.abs(xDp - 116.5f) <= 74f) {
            if (xDp < 116.5f) {
                backFromGesture();
            } else if (state.canToggleDirection()) {
                toggleDirection();
            }
            return;
        }
        int row = FocalList.rowAt(STOPS_TOP_DP, yDp);
        if (row >= 0) {
            state.stopSel.selectRow(row);
            invalidate();
        }
    }

    /** Vertical fling speed converted to rows per second (design units). */
    private float velocityRowsPerSecond(MotionEvent up) {
        if (velocityTracker == null) return 0f;
        velocityTracker.addMovement(up);
        velocityTracker.computeCurrentVelocity(1000, ViewConfiguration.get(getContext()).getMaximumFlingVelocity());
        float vy = velocityTracker.getYVelocity();
        recycleTracker();
        // velocity is in raw pixels, the layout in 466-px design units
        float scale = Displays.scaleFor(getWidth(), getHeight());
        float vyDesign = scale > 0 ? vy / scale : vy;
        float row = rowPitchPx();
        if (row <= 0) return 0f;
        float rowsPerSec = Inertia.rowsPerSecond(vyDesign, row) * 0.8f;
        if (rowsPerSec > 30f) rowsPerSec = 30f;
        if (rowsPerSec < -30f) rowsPerSec = -30f;
        return rowsPerSec;
    }

    /** Row pitch in design px for the current list. */
    private float rowPitchPx() {
        if (state == null) return dp(34f);
        float rowDp = state.screen == WatchState.SCREEN_STOPS
                ? FocalList.rowHeightDp(2)
                : RouteList.rowHeightDp(2);
        return dp(rowDp);
    }

    private void recycleTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private void tapCities(float y) {
        float yDp = y / density;
        int row = RouteList.rowAt(ROUTES_TOP_DP, yDp);
        if (row < 0) return;
        int idx = state.citySel.topIndex() + row;
        if (idx < 0 || idx >= state.cityOptionsCount()) return;
        state.citySel.select(idx);
        invalidate();
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if (listener != null) listener.onCityChosen(idx);
    }

    private void tapRoutes(float x, float y) {
        float yDp = y / density;
        int row = RouteList.rowAt(ROUTES_TOP_DP, yDp);
        if (row >= 0) {
            RouteInfo r = state.routeAt(state.routeSel.topIndex() + row);
            if (r != null) {
                state.routeSel.selectRow(row);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (listener != null) listener.onRouteChosen(r);
            }
        }
    }

    private void tapMenu(float y) {
        float yDp = y / density;
        float step = state.screen == WatchState.SCREEN_SETTINGS ? 34f : 40f;
        int idx = (int) Math.floor((yDp - 76f) / step);
        if (idx >= 0 && idx < state.menuSel.size()) {
            state.menuSel.select(idx);
            invalidate();
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (listener != null) listener.onMenuActivate(idx);
        }
    }

    private float dp(float v) {
        return v * density;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec));
    }
}
