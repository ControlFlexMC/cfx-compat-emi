package com.ifels.cfx.emi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdgeDetectorTest {

    @Test
    void firesOnceWhenHeld() {
        EdgeDetector d = new EdgeDetector();
        assertTrue(d.update(true, false));
        assertFalse(d.update(true, false));
        assertFalse(d.update(true, true));
    }

    @Test
    void firesWhenTapEndedBetweenFrames() {
        EdgeDetector d = new EdgeDetector();
        assertTrue(d.update(false, true));
    }

    @Test
    void silentWithoutInput() {
        EdgeDetector d = new EdgeDetector();
        assertFalse(d.update(false, false));
    }

    @Test
    void firesOnceForTwoClicksInOneFrame() {
        EdgeDetector d = new EdgeDetector();
        assertTrue(d.update(false, true));
        assertFalse(d.update(false, false));
    }

    @Test
    void resetClearsLastDownSoHeldButtonFires() {
        EdgeDetector d = new EdgeDetector();
        d.update(true, false);
        d.reset();
        assertTrue(d.update(true, false));
    }

    @Test
    void seedMarksAButtonAsAlreadyHeldWithoutFiring() {
        EdgeDetector d = new EdgeDetector();
        d.seed(true);
        assertFalse(d.update(true, false));
    }
}
