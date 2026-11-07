package laundromat;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Entry point.
 *
 *   (no arguments)   opens the Swing GUI; choose the NORMAL or CONGESTED scenario with a button
 *   --normal         opens the GUI and starts the normal scenario immediately
 *   --congested      opens the GUI and starts the congested (bonus) scenario immediately
 *   --console        no GUI: console run of the normal scenario
 *   --console --congested   no GUI: console run of the congested scenario
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        boolean console = hasFlag(args, "--console");
        boolean congested = hasFlag(args, "--congested") || hasFlag(args, "congested");
        boolean normal = hasFlag(args, "--normal");

        if (console) {
            runConsole(congested);
        } else {
            LaundryGui.open(congested ? "congested" : (normal ? "normal" : null));
        }
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }

    private static void runConsole(boolean congested) {
        SimulationLogger logger = new SimulationLogger();
        Statistics statistics = new Statistics();
        LaundryFacility facility = new LaundryFacility(statistics, logger, congested);
        CustomerGenerator generator = new CustomerGenerator(facility, statistics, logger);
        Thread generatorThread = new Thread(generator, "Thread-CustomerGenerator");
        ScheduledExecutorService monitor = startStatusMonitor(facility, statistics, logger);

        System.out.println("========================================");
        System.out.println("SMART LAUNDRY FACILITY SIMULATION STARTED");
        System.out.println("Scenario: " + (congested ? "CONGESTED (kiosks unavailable)" : "NORMAL"));
        System.out.println("========================================");

        long startNanos = System.nanoTime();
        generatorThread.start();
        try {
            generatorThread.join();            // generator finished -> all customer threads exist
            generator.awaitAllCustomers();     // wait until every customer has left
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            generatorThread.interrupt();
            generator.interruptAll();
            System.out.println("Simulation interrupted - shutting down.");
        } finally {
            monitor.shutdownNow();
            facility.close();
            boolean wasInterrupted = Thread.interrupted();
            try {
                generatorThread.join();
                generator.awaitAllCustomers();
                facility.awaitOwner();
            } catch (InterruptedException e) {
                wasInterrupted = true;
            } finally {
                if (wasInterrupted) Thread.currentThread().interrupt();
            }
        }
        double durationSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;

        printSummary(statistics, facility, durationSeconds);
    }

    /** Periodically prints a one-line "dashboard" of the laundromat (console visualisation). */
    private static ScheduledExecutorService startStatusMonitor(LaundryFacility facility,
                                                               Statistics statistics,
                                                               SimulationLogger logger) {
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Thread-StatusMonitor");
            thread.setDaemon(true); // never keeps the JVM alive
            return thread;
        });
        int interval = SimulationConfig.STATUS_REPORT_INTERVAL;
        monitor.scheduleAtFixedRate(() -> logger.logSystem("Monitor", String.format(Locale.ROOT,
                        "STATUS | Washers %d/%d | Dryers %d/%d | Waiting for payment: %d | Served: %d/%d",
                        statistics.getCurrentWashers(), SimulationConfig.NUMBER_OF_WASHERS,
                        statistics.getCurrentDryers(), SimulationConfig.NUMBER_OF_DRYERS,
                        facility.getWaitingForPayment(),
                        statistics.getTotalCustomersServed(), SimulationConfig.NUMBER_OF_CUSTOMERS)),
                interval, interval, TimeUnit.SECONDS);
        return monitor;
    }

    /** Prints the final statistics (used by both the console run and the GUI run). */
    static void printSummary(Statistics statistics, LaundryFacility facility, double durationSeconds) {
        System.out.println();
        System.out.println("========================================");
        System.out.println(statistics.getTotalCustomersServed() == SimulationConfig.NUMBER_OF_CUSTOMERS
                ? "SIMULATION COMPLETE" : "SIMULATION CANCELLED / INCOMPLETE");
        System.out.println("========================================");
        System.out.printf(Locale.ROOT, "Total customers served: %d%n", statistics.getTotalCustomersServed());
        System.out.printf(Locale.ROOT, "Average customer time: %.2f seconds%n",
                statistics.getAverageCustomerTimeSeconds());
        System.out.printf(Locale.ROOT, "Maximum washers in use: %d%n", statistics.getMaxWashers());
        System.out.printf(Locale.ROOT, "Maximum dryers in use: %d%n", statistics.getMaxDryers());
        System.out.printf(Locale.ROOT, "Simulation duration: %.2f seconds%n", durationSeconds);
        System.out.printf(Locale.ROOT, "Washing machine failures: %d%n", statistics.getWashFailures());
        System.out.printf(Locale.ROOT, "Payment kiosk failures: %d%n", statistics.getPaymentFailures());
        System.out.printf(Locale.ROOT, "Owner called: %s%n", facility.isOwnerCalled() ? "yes" : "no");
        System.out.printf(Locale.ROOT, "Manual cash payments: %d%n", facility.getManualPayments());
        System.out.printf(Locale.ROOT, "Kiosks in service: %s%n", facility.arePaymentKiosksInService());
        System.out.println("========================================");
    }
}
