package laundromat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DryerTest {

    private final Statistics statistics = new Statistics();
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final SimulationLogger logger = new SimulationLogger(new PrintStream(output));

    @Test
    void dryingCompletesAndUpdatesStatistics() throws InterruptedException {
        Dryer dryer = new Dryer(2, statistics, logger, 20, 30);

        assertEquals(2, dryer.getId());
        dryer.dry(5);

        assertEquals(0, statistics.getCurrentDryers());
        assertEquals(1, statistics.getMaxDryers());
        assertTrue(output.toString().contains("Drying completed in Dryer 2"));
    }

    @Test
    void interruptedDryingDoesNotLeakTheInUseCounter() {
        Dryer dryer = new Dryer(1, statistics, logger, 5_000, 5_000);

        Thread.currentThread().interrupt();
        assertThrows(InterruptedException.class, () -> dryer.dry(1));

        assertEquals(0, statistics.getCurrentDryers());
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void fourDryersCanRunAtTheSameTime() throws Exception {
        int dryerCount = 4;
        ExecutorService pool = Executors.newFixedThreadPool(dryerCount);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 1; i <= dryerCount; i++) {
            Dryer dryer = new Dryer(i, statistics, logger, 300, 300);
            int customerId = i;
            futures.add(pool.submit(() -> {
                startGate.await();
                dryer.dry(customerId);
                return null;
            }));
        }
        startGate.countDown(); // release all four at once
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(dryerCount, statistics.getMaxDryers());
        assertEquals(0, statistics.getCurrentDryers());
    }
}
