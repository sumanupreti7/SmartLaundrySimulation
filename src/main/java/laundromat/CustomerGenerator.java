package laundromat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Creates the customers at random intervals and starts each one in its own thread.
 * Keeps the threads so the caller can wait until every customer has finished.
 */
public class CustomerGenerator implements Runnable {

    private final LaundryFacility facility;
    private final Statistics statistics;
    private final SimulationLogger logger;
    private final int numberOfCustomers;

    // WHY CopyOnWriteArrayList: the generator thread adds to it while Main (or the GUI)
    // may read it, so it must be safe for concurrent access.
    private volatile boolean cancelled;
    private final List<Customer> customers = new CopyOnWriteArrayList<>();
    private final List<Thread> customerThreads = new CopyOnWriteArrayList<>();

    public CustomerGenerator(LaundryFacility facility, Statistics statistics, SimulationLogger logger) {
        this(facility, statistics, logger, SimulationConfig.NUMBER_OF_CUSTOMERS);
    }

    CustomerGenerator(LaundryFacility facility, Statistics statistics, SimulationLogger logger,
                      int numberOfCustomers) {
        this.facility = facility;
        this.statistics = statistics;
        this.logger = logger;
        this.numberOfCustomers = numberOfCustomers;
    }

    @Override
    public void run() {
        for (int id = 1; id <= numberOfCustomers; id++) {
            Customer customer = new Customer(id, facility, statistics, logger);
            Thread thread = new Thread(customer, "Thread-Customer-" + id);
            synchronized (customerThreads) {
                if (cancelled || Thread.currentThread().isInterrupted()) return;
                customers.add(customer);
                customerThreads.add(thread);
                thread.start();
            }

            if (id < numberOfCustomers) {
                long delayMillis = SimulationConfig.randomArrivalMillis();
                try {
                    Thread.sleep(delayMillis); // simulates the random gap between arrivals
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.logSystem("Generator", "Interrupted - no more customers will arrive");
                    return;
                }
            }
        }
        logger.logSystem("Generator", "All " + numberOfCustomers + " customers have arrived");
    }

    /** Waits for every customer thread created so far. Call after the generator has finished. */
    public void awaitAllCustomers() throws InterruptedException {
        for (Thread thread : customerThreads) {
            thread.join();
        }
    }

    /** Interrupts all customer threads (used only when the whole simulation is cancelled). */
    public void interruptAll() {
        synchronized (customerThreads) {
            cancelled = true;
            for (Thread thread : customerThreads) thread.interrupt();
        }
    }

    public List<Customer> getCustomers() { return List.copyOf(customers); }

    public int getNumberOfCustomersCreated() {
        return customerThreads.size();
    }
}
