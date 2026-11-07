package laundromat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StatisticsTest {

    @Test
    void averageIsZeroWhenNobodyServed() {
        Statistics statistics = new Statistics();
        assertEquals(0.0, statistics.getAverageCustomerTimeSeconds(), 1e-9);
    }

    @Test
    void averageTimeIsComputedFromRecordedCustomers() {
        Statistics statistics = new Statistics();
        statistics.recordCustomerServed(10_000);
        statistics.recordCustomerServed(20_000);
        assertEquals(2, statistics.getTotalCustomersServed());
        assertEquals(15.0, statistics.getAverageCustomerTimeSeconds(), 1e-9);
    }

    @Test
    void maximumWashersTracksPeakUsage() {
        Statistics statistics = new Statistics();
        for (int i = 0; i < 6; i++) {
            statistics.washerStarted();
        }
        for (int i = 0; i < 6; i++) {
            statistics.washerFinished();
        }
        statistics.washerStarted();
        statistics.washerFinished();

        assertEquals(0, statistics.getCurrentWashers());
        assertEquals(6, statistics.getMaxWashers());
    }

    @Test
    void maximumDryersTracksPeakUsage() {
        Statistics statistics = new Statistics();
        for (int i = 0; i < 4; i++) {
            statistics.dryerStarted();
        }
        assertEquals(4, statistics.getCurrentDryers());
        for (int i = 0; i < 4; i++) {
            statistics.dryerFinished();
        }
        assertEquals(0, statistics.getCurrentDryers());
        assertEquals(4, statistics.getMaxDryers());
    }

    @Test
    void concurrentUpdatesAreNotLost() throws InterruptedException {
        Statistics statistics = new Statistics();
        int tasks = 500;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch done = new CountDownLatch(tasks);

        for (int i = 0; i < tasks; i++) {
            pool.submit(() -> {
                statistics.washerStarted();
                statistics.washerFinished();
                statistics.dryerStarted();
                statistics.dryerFinished();
                statistics.recordCustomerServed(2_000);
                done.countDown();
            });
        }

        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(tasks, statistics.getTotalCustomersServed());
        assertEquals(2.0, statistics.getAverageCustomerTimeSeconds(), 1e-9);
        assertEquals(0, statistics.getCurrentWashers());
        assertEquals(0, statistics.getCurrentDryers());
        assertTrue(statistics.getMaxWashers() >= 1 && statistics.getMaxWashers() <= tasks);
    }
}
