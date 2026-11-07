package laundromat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

class PaymentKioskTest {

    private final Statistics statistics = new Statistics();
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final SimulationLogger logger = new SimulationLogger(new PrintStream(output));

    @Test
    void successfulPaymentReturnsTrue() throws InterruptedException {
        PaymentKiosk kiosk = new PaymentKiosk(1, statistics, logger, 10, 20, 0.0);

        assertEquals(1, kiosk.getId());
        assertTrue(kiosk.pay(4));

        assertEquals(0, statistics.getPaymentFailures());
        assertTrue(output.toString().contains("Payment completed at Kiosk 1"));
    }

    @Test
    void failedPaymentReturnsFalseAndIsCounted() throws InterruptedException {
        PaymentKiosk kiosk = new PaymentKiosk(2, statistics, logger, 10, 20, 1.0);

        assertFalse(kiosk.pay(4));

        assertEquals(1, statistics.getPaymentFailures());
        assertTrue(kiosk.hasJustFailed());
        assertTrue(output.toString().contains("FAILURE: Payment Kiosk 2"));
    }

    @Test
    void interruptedPaymentThrowsInterruptedException() {
        PaymentKiosk kiosk = new PaymentKiosk(1, statistics, logger, 5_000, 5_000, 0.0);

        Thread.currentThread().interrupt();
        assertThrows(InterruptedException.class, () -> kiosk.pay(1));
        assertEquals(0, kiosk.getCurrentCustomerId());
        assertFalse(Thread.currentThread().isInterrupted());
    }
}
