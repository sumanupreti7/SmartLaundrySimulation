package laundromat;

import java.io.PrintStream;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Thread-safe console logger. Every line shows the actor (customer/owner), the name of
 * the thread that performed the action and the time since the logger was created.
 * An optional listener (used by the GUI) receives every finished line as well.
 */
public class SimulationLogger {

    private final PrintStream out;
    private final Consumer<String> listener; // may be null
    private final long startNanos = System.nanoTime();

    // WHY a lock: many threads print at the same time. Holding a lock while printing
    // guarantees that each log line is written completely and never interleaved.
    private final ReentrantLock printLock = new ReentrantLock();

    public SimulationLogger() {
        this(System.out, null);
    }

    public SimulationLogger(PrintStream out) {
        this(out, null);
    }

    public SimulationLogger(PrintStream out, Consumer<String> listener) {
        this.out = Objects.requireNonNull(out, "out");
        this.listener = listener;
    }

    /** Logs an event performed by a customer, e.g. "[Customer-12][Thread-Customer-12] ...". */
    public void logCustomer(int customerId, String message) {
        log("Customer-" + customerId, message);
    }

    /** Logs an event performed by a non-customer actor such as the Owner or the Monitor. */
    public void logSystem(String actor, String message) {
        log(actor, message);
    }

    private void log(String actor, String message) {
        double elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
        // Build the line first so the lock is held only for the actual printing.
        String line = String.format(Locale.ROOT, "[%6.2fs] [%s][%s] %s",
                elapsedSeconds, actor, Thread.currentThread().getName(), message);
        printLock.lock();
        try {
            out.println(line);
        } finally {
            printLock.unlock();
        }
        if (listener != null) {
            listener.accept(line); // GUI listener only queues the text onto the Swing thread
        }
    }
}
