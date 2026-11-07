package laundromat;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One payment kiosk. LaundryFacility guarantees only one customer uses it at a time.
 * The kiosk is released by the caller (Customer) in a finally block, also after a failure.
 */
public class PaymentKiosk {

    private final int id;
    private final Statistics statistics;
    private final SimulationLogger logger;
    private final long minPaymentMillis;
    private final long maxPaymentMillis;
    private final double failureRate;

    // Display state for the GUI (volatile: written by customer thread, read by Swing timer).
    private volatile int currentCustomerId = 0;   // 0 = idle
    private volatile long failedAtMillis = 0;
    private volatile long busyStartMillis = 0;
    private volatile long busyDurationMillis = 1;

    public PaymentKiosk(int id, Statistics statistics, SimulationLogger logger) {
        this(id, statistics, logger,
                SimulationConfig.MIN_PAYMENT_TIME * SimulationConfig.MILLIS_PER_SECOND,
                SimulationConfig.MAX_PAYMENT_TIME * SimulationConfig.MILLIS_PER_SECOND,
                SimulationConfig.PAYMENT_FAILURE_RATE);
    }

    /** Package-private so unit tests can use very short durations and forced failure rates. */
    PaymentKiosk(int id, Statistics statistics, SimulationLogger logger,
                 long minPaymentMillis, long maxPaymentMillis, double failureRate) {
        this.id = id;
        this.statistics = statistics;
        this.logger = logger;
        this.minPaymentMillis = minPaymentMillis;
        this.maxPaymentMillis = maxPaymentMillis;
        this.failureRate = failureRate;
    }

    public int getId() {
        return id;
    }

    public int getCurrentCustomerId() { return currentCustomerId; }
    /** True for ~1.5 s after a failure so the GUI can flash the kiosk red. */
    public boolean hasJustFailed() { return System.currentTimeMillis() - failedAtMillis < 1500; }

    public double getProgress() {
        if (currentCustomerId == 0) {
            return 0.0;
        }
        double p = (System.currentTimeMillis() - busyStartMillis) / (double) busyDurationMillis;
        return Math.max(0.0, Math.min(1.0, p));
    }

    /**
     * Processes one payment.
     *
     * @return true if payment succeeded, false if the kiosk failed (customer retries later)
     * @throws InterruptedException if the thread is interrupted while paying
     */
    public boolean pay(int customerId) throws InterruptedException {
        long paymentMillis = SimulationConfig.randomMillisBetween(minPaymentMillis, maxPaymentMillis);
        boolean willFail = ThreadLocalRandom.current().nextDouble() < failureRate;

        busyStartMillis = System.currentTimeMillis();
        busyDurationMillis = Math.max(1, paymentMillis);
        currentCustomerId = customerId;
        try {
            logger.logCustomer(customerId, String.format(Locale.ROOT,
                    "Payment started at Kiosk %d (%.1f s)", id, paymentMillis / 1000.0));

            if (willFail) {
                Thread.sleep(paymentMillis / 2);
                statistics.recordPaymentFailure();
                failedAtMillis = System.currentTimeMillis();
                logger.logCustomer(customerId, "FAILURE: Payment Kiosk " + id + " failed during payment");
                return false;
            }

            Thread.sleep(paymentMillis);
            logger.logCustomer(customerId, "Payment completed at Kiosk " + id);
            return true;
        } finally {
            currentCustomerId = 0;
        }
    }
}
