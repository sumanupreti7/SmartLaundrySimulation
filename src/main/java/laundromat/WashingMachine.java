package laundromat;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One physical washing machine. Exclusive access is guaranteed by LaundryFacility
 * (a machine is only handed to one customer at a time), so this class holds no lock.
 */
public class WashingMachine {

    private final int id;
    private final Statistics statistics;
    private final SimulationLogger logger;
    private final long minWashMillis;
    private final long maxWashMillis;
    private final double failureRate;
    private final long recoveryMillis;

    // Display state for the GUI. Written by the customer thread using the machine, read by the
    // Swing timer thread -> volatile guarantees the GUI always sees the latest values.
    private volatile int currentCustomerId = 0;   // 0 = idle
    private volatile boolean broken = false;
    private volatile long busyStartMillis = 0;
    private volatile long busyDurationMillis = 1;

    public WashingMachine(int id, Statistics statistics, SimulationLogger logger) {
        this(id, statistics, logger,
                SimulationConfig.MIN_WASH_TIME * SimulationConfig.MILLIS_PER_SECOND,
                SimulationConfig.MAX_WASH_TIME * SimulationConfig.MILLIS_PER_SECOND,
                SimulationConfig.WASH_FAILURE_RATE,
                SimulationConfig.WASH_FAILURE_RECOVERY_TIME * SimulationConfig.MILLIS_PER_SECOND);
    }

    /** Package-private so unit tests can use very short durations and forced failure rates. */
    WashingMachine(int id, Statistics statistics, SimulationLogger logger,
                   long minWashMillis, long maxWashMillis, double failureRate, long recoveryMillis) {
        this.id = id;
        this.statistics = statistics;
        this.logger = logger;
        this.minWashMillis = minWashMillis;
        this.maxWashMillis = maxWashMillis;
        this.failureRate = failureRate;
        this.recoveryMillis = recoveryMillis;
    }

    public int getId() {
        return id;
    }

    // ----- GUI state -----
    public int getCurrentCustomerId() { return currentCustomerId; }
    public boolean isBroken() { return broken; }

    /** Progress of the current cycle between 0.0 and 1.0. */
    public double getProgress() {
        if (currentCustomerId == 0) {
            return 0.0;
        }
        double p = (System.currentTimeMillis() - busyStartMillis) / (double) busyDurationMillis;
        return Math.max(0.0, Math.min(1.0, p));
    }

    /**
     * Runs one washing cycle for the given customer.
     *
     * @return true if the wash finished, false if the machine failed (customer must retry)
     * @throws InterruptedException if the thread is interrupted while washing
     */
    public boolean wash(int customerId) throws InterruptedException {
        statistics.washerStarted();
        try {
            long washMillis = SimulationConfig.randomMillisBetween(minWashMillis, maxWashMillis);
            boolean willFail = ThreadLocalRandom.current().nextDouble() < failureRate;

            busyStartMillis = System.currentTimeMillis();
            busyDurationMillis = Math.max(1, washMillis);
            broken = false;
            currentCustomerId = customerId;

            logger.logCustomer(customerId, String.format(Locale.ROOT,
                    "Using Washing Machine %d - washing started (%.1f s)", id, washMillis / 1000.0));

            if (willFail) {
                // The machine breaks half-way through the cycle.
                Thread.sleep(washMillis / 2);
                statistics.recordWashFailure();
                broken = true;
                logger.logCustomer(customerId, "FAILURE: Washing Machine " + id
                        + " broke down during the washing cycle");
                logger.logCustomer(customerId, "Washing Machine " + id + " is being reset - customer must wait");
                Thread.sleep(recoveryMillis);
                return false;
            }

            Thread.sleep(washMillis);
            logger.logCustomer(customerId, "Washing completed in Washing Machine " + id);
            return true;
        } finally {
            // WHY finally: the "in use" counter must go down even on failure or interruption,
            // otherwise the statistics would permanently over-count busy washers.
            broken = false;
            currentCustomerId = 0;
            statistics.washerFinished();
        }
    }
}
