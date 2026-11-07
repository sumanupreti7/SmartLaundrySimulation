package laundromat;

/**
 * One customer = one thread. The customer only uses the shared facility; it never touches
 * another customer's thread.
 */
public class Customer implements Runnable {

    private final int id;
    private final LaundryFacility facility;
    private final Statistics statistics;
    private final SimulationLogger logger;

    // Written by the customer's own thread, read by others -> volatile for visibility.
    /** One immutable snapshot publishes stage/state together to the GUI. */
    public record Activity(String stage, String state, long changedNanos) { }
    private volatile Activity activity = new Activity("Entry", "Arriving", System.nanoTime());
    public Activity getActivity() { return activity; }
    public String getStage() { return activity.stage(); }
    public String getState() { return activity.state(); }
    private void activity(String stage, String state) {
        activity = new Activity(stage, state, System.nanoTime());
    }

    private volatile long arrivalTimeMillis;
    private volatile long totalTimeMillis = -1;

    public Customer(int id, LaundryFacility facility, Statistics statistics, SimulationLogger logger) {
        this.id = id;
        this.facility = facility;
        this.statistics = statistics;
        this.logger = logger;
    }

    public int getId() {
        return id;
    }

    public long getArrivalTimeMillis() {
        return arrivalTimeMillis;
    }

    /** Total time spent in the laundromat, or -1 if the customer has not finished. */
    public long getTotalTimeMillis() {
        return totalTimeMillis;
    }

    @Override
    public void run() {
        long arrivalNanos = System.nanoTime();
        arrivalTimeMillis = System.currentTimeMillis();
        try {
            logger.logCustomer(id, "Arrived at laundromat");
            washClothes();
            dryClothes();
            payForService();

            long elapsedMillis = (System.nanoTime() - arrivalNanos) / 1_000_000L;
            totalTimeMillis = elapsedMillis;
            statistics.recordCustomerServed(elapsedMillis);
            activity("Exit", "Served");
            logger.logCustomer(id, "Exited laundromat");
        } catch (InterruptedException e) {
            // Any resource held was already released by the finally blocks below.
            activity("Exit", "Cancelled");
            Thread.currentThread().interrupt(); // restore the interrupt status
            logger.logCustomer(id, "Interrupted - leaving the laundromat early");
        }
    }

    private void washClothes() throws InterruptedException {
        boolean washed = false;
        int attempt = 0;
        while (!washed) {
            attempt++;
            activity("Washing", "Waiting");
            logger.logCustomer(id, "Waiting for washing machine");
            
            WashingMachine washer = facility.acquireWasher();
            try {
                logger.logCustomer(id, "Acquired Washing Machine " + washer.getId());
                activity("Washing", "Active");
                washed = washer.wash(id);
            } finally {
                // WHY finally: the machine must return to the pool even after a failure
                // or interruption, otherwise other customers would wait forever.
                facility.releaseWasher(washer);
            }
            if (!washed) {
                logger.logCustomer(id, "Washing retry (attempt " + (attempt + 1) + ")");
            }
        }
    }

    private void dryClothes() throws InterruptedException {
        activity("Drying", "Waiting");
        logger.logCustomer(id, "Waiting for dryer");
        Dryer dryer = facility.acquireDryer();
        try {
            logger.logCustomer(id, "Acquired Dryer " + dryer.getId());
            activity("Drying", "Active");
            dryer.dry(id);
        } finally {
            facility.releaseDryer(dryer);
        }
    }

    private void payForService() throws InterruptedException {
        if (facility.isCongestedScenario()) {
            activity("Payment", "Waiting");
            logger.logCustomer(id, "Both kiosks offline - waiting for the owner's manual counter");
            facility.acquireManualCounter(id);
            try {
                activity("Payment", "Active");
                logger.logCustomer(id, "Handing over cash at the owner's manual counter");
                Thread.sleep(facility.getManualPaymentMillis());
                facility.recordManualPayment();
                logger.logCustomer(id, "Manual payment completed");
            } finally {
                facility.releaseManualCounter();
            }
            return;
        }
        boolean paid = false;
        while (!paid) {
            activity("Payment", "Waiting");
            logger.logCustomer(id, "Waiting for payment kiosk");
            PaymentKiosk kiosk = facility.acquirePaymentKiosk(id);
            try {
                logger.logCustomer(id, "Acquired Payment Kiosk " + kiosk.getId());
                activity("Payment", "Active");
                paid = kiosk.pay(id);
            } finally {
                facility.releasePaymentKiosk(kiosk);
            }
            if (!paid) {
                activity("Payment", "Retrying");
                logger.logCustomer(id, "Payment retry after "
                        + SimulationConfig.PAYMENT_RETRY_DELAY + " seconds");
                // Sleep is used only to simulate the required retry delay. The kiosk was already
                // released above, so other customers can use it meanwhile.
                Thread.sleep(SimulationConfig.PAYMENT_RETRY_DELAY * SimulationConfig.MILLIS_PER_SECOND);
            }
        }
    }
}

