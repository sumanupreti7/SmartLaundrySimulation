package laundromat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

class WashingMachineTest {

    private final Statistics statistics = new Statistics();
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final SimulationLogger logger = new SimulationLogger(new PrintStream(output));

    @Test
    void successfulWashReturnsTrueAndUpdatesStatistics() throws InterruptedException {
        WashingMachine machine = new WashingMachine(3, statistics, logger, 20, 30, 0.0, 10);

        assertEquals(3, machine.getId());
        assertTrue(machine.wash(7));

        assertEquals(0, statistics.getCurrentWashers());
        assertEquals(1, statistics.getMaxWashers());
        assertEquals(0, statistics.getWashFailures());
        assertEquals(0, machine.getCurrentCustomerId()); // GUI state is cleared afterwards
        assertTrue(output.toString().contains("Washing completed in Washing Machine 3"));
    }

    @Test
    void failedWashReturnsFalseAndStillReleasesTheWasherCounter() throws InterruptedException {
        WashingMachine machine = new WashingMachine(1, statistics, logger, 20, 30, 1.0, 10);

        assertFalse(machine.wash(2));

        assertEquals(1, statistics.getWashFailures());
        assertEquals(0, statistics.getCurrentWashers());
        assertFalse(machine.isBroken());
        assertTrue(output.toString().contains("FAILURE"));
    }

    @Test
    void interruptedWashThrowsAndDoesNotLeakTheInUseCounter() {
        WashingMachine machine = new WashingMachine(1, statistics, logger, 5_000, 5_000, 0.0, 10);

        Thread.currentThread().interrupt(); // sleep() will throw immediately
        assertThrows(InterruptedException.class, () -> machine.wash(1));

        assertEquals(0, statistics.getCurrentWashers());
        assertEquals(0, machine.getCurrentCustomerId());
        assertFalse(Thread.currentThread().isInterrupted()); // flag cleared by the exception
    }
}
