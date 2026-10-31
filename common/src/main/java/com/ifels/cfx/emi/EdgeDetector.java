package com.ifels.cfx.emi;

/**
 * Turns raw key state into one "fire" per press (spec §7.3).
 *
 * <p>Two signals are combined: a rising edge of {@code isDown} (the normal path — ControlFlex
 * keeps a one-shot action pressed until the next tick), and {@code clicked} as a fallback for a
 * press that began and ended between two samples. ControlFlex increments {@code clickCount} on
 * <em>every</em> {@code setPressed(true)}, so a held HOLD-mode binding produces clicks every
 * tick; the rising-edge branch is what keeps that from firing repeatedly.</p>
 */
public final class EdgeDetector {

    private boolean lastDown;

    public boolean update(boolean isDown, boolean clicked) {
        boolean fire = (isDown && !lastDown) || (!isDown && clicked);
        lastDown = isDown;
        return fire;
    }

    public void reset() {
        lastDown = false;
    }
}
