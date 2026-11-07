package laundromat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomerTest {

    private final Statistics statistics = new Statistics();
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final SimulationLogger logger = new SimulationLogger(new PrintStream(output));

    /** Builds a small, fast facility with no random failures. */
    private LaundryFacility fastFacility(int washers, int dryers, int kiosks) {
        List<WashingMachine> washerList = new ArrayList<>();
        for (int i = 1; i <= washers; i++) {
            washerList.add(new WashingMachine(i, statistics, logger, 30, 30, 0.0, 10));
        }
        List<Dryer> dryerList = new ArrayList<>();
        for (int i = 1; i <= dryers; i++) {
            dryerList.add(new Dryer(i, statistics, logger, 30, 30));
        }
        List<PaymentKiosk> kioskList = new ArrayList<>();
        for (int i = 1; i <= kiosks; i++) {
            kioskList.add(new PaymentKiosk(i, statistics, logger, 30, 30, 0.0));
        }
        return new LaundryFacility(logger, washerList, dryerList, kioskList, false);
    }

    @Test
    void customerCompletesWholeCycleAndIsCounted() throws InterruptedException {
        Customer customer = new Customer(1, fastFacility(1, 1, 1), statistics, logger);
        assertEquals(1, customer.getId());
        assertEquals(-1, customer.getTotalTimeMillis());

        Thread thread = new Thread(customer, "Thread-Customer-1");
        thread.start();
        thread.join(5_000);

        assertFalse(thread.isAlive());
        assertEquals(1, statistics.getTotalCustomersServed());
        assertTrue(customer.getTotalTimeMillis() >= 80, "wash + dry + pay take at least ~90 ms");
        assertTrue(customer.getArrivalTimeMillis() > 0);
        String log = output.toString();
        assertTrue(log.contains("[Customer-1][Thread-Customer-1] Arrived at laundromat"));
        assertTrue(log.contains("Exited laundromat"));
    }

    @Test
    void manyCustomersNeverExceedResourceCapacity() throws InterruptedException {
        LaundryFacility facility = fastFacility(2, 2, 1);
        List<Thread> threads = new ArrayList<>();
        int customers = 12;

        for (int id = 1; id <= customers; id++) {
            Thread thread = new Thread(new Customer(id, facility, statistics, logger), "Thread-Customer-" + id);
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join(10_000);
            assertFalse(thread.isAlive());
        }

        assertEquals(customers, statistics.getTotalCustomersServed());
        assertTrue(statistics.getMaxWashers() <= 2, "at most 2 washers may be in use");
        assertTrue(statistics.getMaxDryers() <= 2, "at most 2 dryers may be in use");
        assertTrue(statistics.getMaxWashers() >= 1);
        assertEquals(0, statistics.getCurrentWashers());
        assertEquals(0, statistics.getCurrentDryers());
    }

    @Test
    void customerInterruptedWhileWaitingLeavesWithoutStealingAResource() throws InterruptedException {
        LaundryFacility facility = fastFacility(1, 1, 1);
        WashingMachine heldWasher = facility.acquireWasher(); // test occupies the only washer

        Thread thread = new Thread(new Customer(1, facility, statistics, logger), "Thread-Customer-1");
        thread.start();
        Thread.sleep(150); // give the customer time to block on the washer
        thread.interrupt();
        thread.join(5_000);

        assertFalse(thread.isAlive());
        assertEquals(0, statistics.getTotalCustomersServed());
        assertEquals(0, facility.getWaitingForWasher()); // waiting counter was decremented in finally

        facility.releaseWasher(heldWasher);
        WashingMachine again = facility.acquireWasher(); // would hang if a permit had leaked
        assertNotNull(again);
        facility.releaseWasher(again);
    }
}
