package laundromat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe statistics shared by all customer threads.
 *
 * WHY atomics: a plain "counter++" is a read-modify-write and loses updates when several
 * threads do it at once. Atomic classes make each update indivisible without a global lock.
 */
public class Statistics {

    private final AtomicInteger totalCustomersServed = new AtomicInteger();
    private final AtomicLong totalCustomerTimeMillis = new AtomicLong();

    private final AtomicInteger currentWashers = new AtomicInteger();
    private final AtomicInteger maxWashers = new AtomicInteger();

    private final AtomicInteger currentDryers = new AtomicInteger();
    private final AtomicInteger maxDryers = new AtomicInteger();

    private final AtomicInteger washFailures = new AtomicInteger();
    private final AtomicInteger paymentFailures = new AtomicInteger();

    // ----- Washers -----

    public void washerStarted() {
        // incrementAndGet returns the exact value produced by THIS increment, so comparing
        // that value with the maximum can never miss a peak (unlike get() after increment).
        int nowInUse = currentWashers.incrementAndGet();
        maxWashers.accumulateAndGet(nowInUse, Math::max);
    }

    public void washerFinished() {
        currentWashers.decrementAndGet();
    }

    // ----- Dryers -----

    public void dryerStarted() {
        int nowInUse = currentDryers.incrementAndGet();
        maxDryers.accumulateAndGet(nowInUse, Math::max);
    }

    public void dryerFinished() {
        currentDryers.decrementAndGet();
    }

    // ----- Customers -----

    public void recordCustomerServed(long totalTimeMillis) {
        totalCustomerTimeMillis.addAndGet(totalTimeMillis);
        totalCustomersServed.incrementAndGet();
    }

    // ----- Failures -----

    public void recordWashFailure() {
        washFailures.incrementAndGet();
    }

    public void recordPaymentFailure() {
        paymentFailures.incrementAndGet();
    }

    // ----- Getters -----

    public int getTotalCustomersServed() {
        return totalCustomersServed.get();
    }

    public long getTotalCustomerTimeMillis() {
        return totalCustomerTimeMillis.get();
    }

    /**
     * Average time per customer in seconds. The two atomics are read separately, so the value
     * is exact once the simulation is finished (which is when it is used for the summary).
     */
    public double getAverageCustomerTimeSeconds() {
        int served = totalCustomersServed.get();
        if (served == 0) {
            return 0.0;
        }
        return totalCustomerTimeMillis.get() / 1000.0 / served;
    }

    public int getCurrentWashers() {
        return currentWashers.get();
    }

    public int getMaxWashers() {
        return maxWashers.get();
    }

    public int getCurrentDryers() {
        return currentDryers.get();
    }

    public int getMaxDryers() {
        return maxDryers.get();
    }

    public int getWashFailures() {
        return washFailures.get();
    }

    public int getPaymentFailures() {
        return paymentFailures.get();
    }
}
