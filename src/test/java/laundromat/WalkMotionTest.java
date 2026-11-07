package laundromat;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WalkMotionTest {
    @Test void longFrameReachesDestinationWithoutOvershoot() {
        WalkMotion motion = new WalkMotion(0, 0, 100);
        motion.target(30, 40, false);
        motion.advance(.25);
        assertEquals(15, motion.x(), 1e-9);
        assertEquals(20, motion.y(), 1e-9);
        motion.advance(10);
        assertEquals(30, motion.x(), 1e-9);
        assertEquals(40, motion.y(), 1e-9);
        assertFalse(motion.moving());
    }
    @Test void retargetingAnAdvancingQueueDoesNotTeleportCustomer() {
        WalkMotion motion = new WalkMotion(100, 500, 100);
        motion.target(200, 500, false);
        motion.advance(.4);
        double current = motion.x();
        motion.target(160, 500, false);
        assertEquals(current, motion.x(), 1e-9);
        assertEquals(500, motion.y(), 1e-9);
        motion.advance(.1);
        assertEquals(current + 10, motion.x(), 1e-9);
        motion.advance(1);
        assertEquals(160, motion.x(), 1e-9);
        assertFalse(motion.moving());
    }
    @Test void crossFacilityMovementUsesAisleInsteadOfCuttingThroughMachines() {
        WalkMotion motion = new WalkMotion(100, 600, 100);
        motion.target(900, 330, true);
        motion.advance(1);
        assertEquals(100, motion.x(), 1e-9);
        assertEquals(500, motion.y(), 1e-9);
        motion.advance(1.04);
        assertEquals(100, motion.x(), 1e-9);
        assertEquals(396, motion.y(), 1e-9);
        motion.advance(1);
        assertEquals(200, motion.x(), 1e-9);
        assertEquals(396, motion.y(), 1e-9);
        motion.advance(10);
        assertEquals(900, motion.x(), 1e-9);
        assertEquals(330, motion.y(), 1e-9);
        assertFalse(motion.moving());
    }
}

