package laundromat;

import java.util.Locale;

/**
 * One physical dryer. LaundryFacility guarantees only one customer uses it at a time.
 */
public class Dryer {

    private final int id;
    private final Statistics statistics;
    private final SimulationLogger logger;
    private final long minDryMillis;
    private final long maxDryMillis;

    // Display state for the GUI (volatile: written by customer thread, read by Swing timer).
    private volatile int currentCustomerId = 0;   // 0 = idle
    private volatile long busyStartMillis = 0;
    private volatile long busyDurationMillis = 1;

    public Dryer(int id, Statistics statistics, SimulationLogger logger) {
        this(id, statistics, logger,
                SimulationConfig.MIN_DRY_TIME * SimulationConfig.MILLIS_PER_SECOND,
                SimulationConfig.MAX_DRY_TIME * SimulationConfig.MILLIS_PER_SECOND);
    }

    /** Package-private so unit tests can use very short durations. */
    Dryer(int id, Statistics statistics, SimulationLogger logger, long minDryMillis, long maxDryMillis) {
        this.id = id;
        this.statistics = statistics;
        this.logger = logger;
        this.minDryMillis = minDryMillis;
        this.maxDryMillis = maxDryMillis;
    }

    public int getId() {
        return id;
    }

    public int getCurrentCustomerId() { return currentCustomerId; }

    public double getProgress() {
        if (currentCustomerId == 0) {
            return 0.0;
        }
        double p = (System.currentTimeMillis() - busyStartMillis) / (double) busyDurationMillis;
        return Math.max(0.0, Math.min(1.0, p));
    }

    /**
     * Runs one drying cycle for the given customer.
     *
     * @throws InterruptedException if the thread is interrupted while drying
     */
    public void dry(int customerId) throws InterruptedException {
        statistics.dryerStarted();
        try {
            long dryMillis = SimulationConfig.randomMillisBetween(minDryMillis, maxDryMillis);
            busyStartMillis = System.currentTimeMillis();
            busyDurationMillis = Math.max(1, dryMillis);
            currentCustomerId = customerId;
            logger.logCustomer(customerId, String.format(Locale.ROOT,
                    "Using Dryer %d - drying started (%.1f s)", id, dryMillis / 1000.0));
            Thread.sleep(dryMillis);
            logger.logCustomer(customerId, "Drying completed in Dryer " + id);
        } finally {
            // WHY finally: keeps the concurrent-dryer statistic correct even if interrupted.
            currentCustomerId = 0;
            statistics.dryerFinished();
        }
    }
}
