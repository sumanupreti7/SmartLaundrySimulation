package laundromat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns all washers, dryers and payment kiosks and controls access to them.
 *
 * Deadlock prevention: a customer never holds two kinds of resource at once. The washer is
 * released before the dryer is requested, and the dryer before the kiosk. Without
 * hold-and-wait, a circular wait cannot form.
 */
public class LaundryFacility {

    private final SimulationLogger logger;
    private final List<WashingMachine> washers;
    private final List<Dryer> dryers;
    private final List<PaymentKiosk> kiosks;
    private final ResourcePool<WashingMachine> washerPool;
    private final ResourcePool<Dryer> dryerPool;
    private final ResourcePool<PaymentKiosk> kioskPool;
    private final boolean congestedScenario;
    private final Semaphore manualCounter = new Semaphore(0, true);
    private final AtomicInteger manualPayments = new AtomicInteger();
    private final Object ownerLifecycle = new Object();
    private volatile Thread ownerThread;
    private volatile boolean closed;
    private volatile int manualCustomer;
    private final long ownerResponseMillis;
    private final long manualPaymentMillis;

    private final AtomicInteger waitingForWasher = new AtomicInteger();
    private final AtomicInteger waitingForDryer = new AtomicInteger();
    private final AtomicInteger waitingForPayment = new AtomicInteger();
    private final AtomicBoolean kiosksOutOfService = new AtomicBoolean();
    private final AtomicBoolean ownerCalled = new AtomicBoolean();
    // Owner state for the GUI: 0 = not called, 1 = on the way, 2 = manual counter open
    private final AtomicInteger ownerState = new AtomicInteger();

    /** Creates the facility using the numbers of resources defined in SimulationConfig. */
    public LaundryFacility(Statistics statistics, SimulationLogger logger, boolean congestedScenario) {
        this(logger,
                createWashers(statistics, logger),
                createDryers(statistics, logger),
                createKiosks(statistics, logger),
                congestedScenario);
    }

    /** Package-private so tests can build small/fast facilities. */
    LaundryFacility(SimulationLogger logger, List<WashingMachine> washers, List<Dryer> dryers,
                    List<PaymentKiosk> kiosks, boolean congestedScenario) {
        this(logger, washers, dryers, kiosks, congestedScenario,
                SimulationConfig.OWNER_RESPONSE_TIME * SimulationConfig.MILLIS_PER_SECOND,
                SimulationConfig.MANUAL_PAYMENT_MILLIS);
    }

    LaundryFacility(SimulationLogger logger, List<WashingMachine> washers, List<Dryer> dryers,
                    List<PaymentKiosk> kiosks, boolean congestedScenario,
                    long ownerResponseMillis, long manualPaymentMillis) {
        this.logger = logger;
        this.congestedScenario = congestedScenario;
        this.ownerResponseMillis = ownerResponseMillis;
        this.manualPaymentMillis = manualPaymentMillis;
        this.washers = Collections.unmodifiableList(new ArrayList<>(washers));
        this.dryers = Collections.unmodifiableList(new ArrayList<>(dryers));
        this.kiosks = Collections.unmodifiableList(new ArrayList<>(kiosks));
        this.washerPool = new ResourcePool<>(washers, washers.size());
        this.dryerPool = new ResourcePool<>(dryers, dryers.size());
        // Congested scenario: kiosks exist but have 0 permits, so nobody can pay
        // for the entire day; customers use a separate manual counter after the owner arrives.
        this.kioskPool = new ResourcePool<>(kiosks, congestedScenario ? 0 : kiosks.size());
        this.kiosksOutOfService.set(congestedScenario);

        if (congestedScenario) {
            logger.logSystem("Facility", "CONGESTED SCENARIO: all payment kiosks are out of service today");
        }
    }

    private static List<WashingMachine> createWashers(Statistics statistics, SimulationLogger logger) {
        List<WashingMachine> list = new ArrayList<>();
        for (int id = 1; id <= SimulationConfig.NUMBER_OF_WASHERS; id++) {
            list.add(new WashingMachine(id, statistics, logger));
        }
        return list;
    }

    private static List<Dryer> createDryers(Statistics statistics, SimulationLogger logger) {
        List<Dryer> list = new ArrayList<>();
        for (int id = 1; id <= SimulationConfig.NUMBER_OF_DRYERS; id++) {
            list.add(new Dryer(id, statistics, logger));
        }
        return list;
    }

    private static List<PaymentKiosk> createKiosks(Statistics statistics, SimulationLogger logger) {
        List<PaymentKiosk> list = new ArrayList<>();
        for (int id = 1; id <= SimulationConfig.NUMBER_OF_PAYMENT_KIOSKS; id++) {
            list.add(new PaymentKiosk(id, statistics, logger));
        }
        return list;
    }

    // ----- Washers -----

    /** Blocks (without busy waiting) until a washing machine is free, then returns it. */
    public WashingMachine acquireWasher() throws InterruptedException {
        waitingForWasher.incrementAndGet();
        try {
            return washerPool.acquire();
        } finally {
            waitingForWasher.decrementAndGet();
        }
    }

    public void releaseWasher(WashingMachine washer) {
        washerPool.release(washer);
    }

    // ----- Dryers -----

    public Dryer acquireDryer() throws InterruptedException {
        waitingForDryer.incrementAndGet();
        try {
            return dryerPool.acquire();
        } finally {
            waitingForDryer.decrementAndGet();
        }
    }

    public void releaseDryer(Dryer dryer) {
        dryerPool.release(dryer);
    }

    // ----- Payment kiosks -----

    /**
     * Blocks until a payment kiosk is free. The number of waiting customers is tracked; in the
     * congested scenario, reaching the threshold calls the owner (exactly once).
     */
    public PaymentKiosk acquirePaymentKiosk(int customerId) throws InterruptedException {
        if (congestedScenario) throw new IllegalStateException("Both kiosks are offline for the day");
        waitingForPayment.incrementAndGet();
        try { return kioskPool.acquire(); }
        finally { waitingForPayment.decrementAndGet(); }
    }

    public void releasePaymentKiosk(PaymentKiosk kiosk) { kioskPool.release(kiosk); }

    public void acquireManualCounter(int customerId) throws InterruptedException {
        synchronized (ownerLifecycle) {
            if (closed) throw new InterruptedException("Facility closed");
            int waiting = waitingForPayment.incrementAndGet();
            try {
                if (waiting >= SimulationConfig.OWNER_CALL_THRESHOLD
                        && ownerCalled.compareAndSet(false, true)) {
                    ownerState.set(1);
                    logger.logCustomer(customerId, "Payment queue has " + waiting
                            + " waiting customers - CALLING THE OWNER!");
                    ownerThread = new Thread(this::ownerOpensManualCounter, "Thread-Owner");
                    ownerThread.start();
                }
            } catch (RuntimeException e) {
                waitingForPayment.decrementAndGet();
                throw e;
            }
        }
        try {
            manualCounter.acquire();
            manualCustomer = customerId;
        } finally {
            waitingForPayment.decrementAndGet();
        }
    }

    private void ownerOpensManualCounter() {
        try {
            logger.logSystem("Owner", "Called; on the way to open a manual cash counter");
            Thread.sleep(ownerResponseMillis);
            synchronized (ownerLifecycle) {
                if (closed) return;
                ownerState.set(2);
                logger.logSystem("Owner", "Manual counter OPEN; both kiosks remain broken for the day");
                manualCounter.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.logSystem("Owner", "Visit cancelled; simulation stopped");
        }
    }

    public void releaseManualCounter() { manualCustomer = 0; manualCounter.release(); }
    public void recordManualPayment() { manualPayments.incrementAndGet(); }
    public long getManualPaymentMillis() { return manualPaymentMillis; }
    public int getManualCustomer() { return manualCustomer; }
    public int getManualPayments() { return manualPayments.get(); }
    public boolean isCongestedScenario() { return congestedScenario; }

    public void close() {
        synchronized (ownerLifecycle) {
            closed = true;
            if (ownerThread != null) ownerThread.interrupt();
        }
    }
    public void awaitOwner() throws InterruptedException {
        Thread t = ownerThread;
        if (t != null) t.join();
    }

    // ----- Status (used by the GUI, monitor and tests) -----

    public List<WashingMachine> getWashers() { return washers; }
    public List<Dryer> getDryers() { return dryers; }
    public List<PaymentKiosk> getKiosks() { return kiosks; }

    public int getWaitingForWasher() { return waitingForWasher.get(); }
    public int getWaitingForDryer() { return waitingForDryer.get(); }

    public int getWaitingForPayment() {
        return waitingForPayment.get();
    }

    public boolean isOwnerCalled() {
        return ownerCalled.get();
    }

    /** 0 = not called, 1 = on the way, 2 = manual counter open. */
    public int getOwnerState() {
        return ownerState.get();
    }

    public boolean arePaymentKiosksInService() {
        return !kiosksOutOfService.get();
    }

    /**
     * A pool of identical resources.
     *
     * The Semaphore limits HOW MANY customers may hold a resource (capacity) and makes waiting
     * threads sleep instead of spinning. The queue remembers WHICH physical resource is idle so
     * the output can name the exact machine. Invariant: permits never exceed idle items, so
     * poll() after a successful acquire always finds an item.
     */
    private static final class ResourcePool<T> {
        private final Semaphore permits;
        private final Queue<T> idleItems = new ConcurrentLinkedQueue<>();

        ResourcePool(Collection<T> items, int initialPermits) {
            if (items.isEmpty()) {
                throw new IllegalArgumentException("A resource pool needs at least one resource");
            }
            idleItems.addAll(items);
             
            this.permits = new Semaphore(initialPermits, true);
        }

        T acquire() throws InterruptedException {
            permits.acquire(); // If interrupted here, no permit was taken, so nothing to release.
            T item = idleItems.poll();
            if (item == null) {
                permits.release();
                throw new IllegalStateException("Semaphore permit granted but no idle resource found");
            }
            return item;
        }

        void release(T item) {
            // Put the item back BEFORE releasing the permit, otherwise a waiting thread could
            // be woken up and find the queue empty.
            idleItems.offer(item);
            permits.release();
        }

    }
}
