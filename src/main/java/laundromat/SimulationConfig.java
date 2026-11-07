package laundromat;

import java.util.concurrent.ThreadLocalRandom;

/**
 * All configurable simulation constants live here so nothing is hard-coded elsewhere.
 * All time values are in SECONDS unless the name says otherwise.
 */
public final class SimulationConfig {

    private SimulationConfig() {
        // Constants holder - not meant to be instantiated.
    }

    // ----- Population and resources -----
    public static final int NUMBER_OF_CUSTOMERS = 50;
    public static final int NUMBER_OF_WASHERS = 6;
    public static final int NUMBER_OF_DRYERS = 4;
    public static final int NUMBER_OF_PAYMENT_KIOSKS = 2;

    // ----- Timings (seconds) -----
    public static final int MIN_ARRIVAL_TIME = 0;
    public static final int MAX_ARRIVAL_TIME = 3;
    public static final int MIN_WASH_TIME = 4;
    public static final int MAX_WASH_TIME = 6;
    public static final int MIN_DRY_TIME = 3;
    public static final int MAX_DRY_TIME = 5;
    public static final int MIN_PAYMENT_TIME = 1;
    public static final int MAX_PAYMENT_TIME = 2;

    // ----- Failures -----
    public static final double WASH_FAILURE_RATE = 0.05;
    public static final double PAYMENT_FAILURE_RATE = 0.05;
    /** Time a failed washing machine needs to reset; the customer waits during this time. */
    public static final int WASH_FAILURE_RECOVERY_TIME = 2;
    /** Delay before a customer retries after a payment kiosk failure. */
    public static final int PAYMENT_RETRY_DELAY = 2;

    // ----- Bonus: congested scenario -----
    /** Number of customers waiting for payment that triggers the call to the owner. */
    public static final int OWNER_CALL_THRESHOLD = 30;
    /** Time the owner needs to arrive and open a manual cash-payment counter. */
    public static final int OWNER_RESPONSE_TIME = 5;

    /** One customer at the manual cash counter; one-second cash handover. */
    public static final long MANUAL_PAYMENT_MILLIS = 1000L;

    /**
     * Full 0-3 second arrival range, with shorter gaps favoured (mean 1 second).
     * This documented assumption targets roughly a minute without shortening cycles.
     * Set -Dlaundry.uniformArrivals=true for uniform 0-3 second arrivals.
     */
    public static long randomArrivalMillis() {
        if (Boolean.getBoolean("laundry.uniformArrivals"))
            return randomMillisBetween(0, MAX_ARRIVAL_TIME * MILLIS_PER_SECOND);
        double u = ThreadLocalRandom.current().nextDouble();
        return (long) (MAX_ARRIVAL_TIME * MILLIS_PER_SECOND * u * u);
    }

    // ----- Monitoring -----
    public static final int STATUS_REPORT_INTERVAL = 10;

    public static final long MILLIS_PER_SECOND = 1000L;

    /**
     * Returns a random duration between min and max (inclusive) in milliseconds.
     * ThreadLocalRandom is used because many threads ask for random numbers at once and
     * it avoids contention on a single shared Random instance.
     */
    public static long randomMillisBetween(long minMillis, long maxMillis) {
        if (maxMillis <= minMillis) {
            return minMillis;
        }
        return ThreadLocalRandom.current().nextLong(minMillis, maxMillis + 1);
    }
}
