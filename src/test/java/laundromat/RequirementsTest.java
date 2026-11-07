package laundromat;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RequirementsTest {
    private LaundryFacility facility(Statistics stats, SimulationLogger log, boolean congested) {
        List<WashingMachine> w = new ArrayList<>();
        List<Dryer> d = new ArrayList<>();
        List<PaymentKiosk> k = new ArrayList<>();
        for (int i=1;i<=6;i++) w.add(new WashingMachine(i,stats,log,12,12,0,1));
        for (int i=1;i<=4;i++) d.add(new Dryer(i,stats,log,12,12));
        for (int i=1;i<=2;i++) k.add(new PaymentKiosk(i,stats,log,12,12,0));
        return new LaundryFacility(log,w,d,k,congested,20,3);
    }
    private void awaitQueue(LaundryFacility f, int count) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(f.getWaitingForPayment()<count && System.nanoTime()<deadline) Thread.sleep(2);
        assertEquals(count,f.getWaitingForPayment());
    }

    @Test void ownerCalledAtThirtyAndKiosksStayBroken() throws Exception {
        Statistics stats=new Statistics();ByteArrayOutputStream output=new ByteArrayOutputStream();
        LaundryFacility f=facility(stats,new SimulationLogger(new PrintStream(output)),true);
        List<Thread> threads=new ArrayList<>();
        try {
            for(int id=1;id<=29;id++) {
                int customer=id;
                Thread t=new Thread(()-> {try {f.acquireManualCounter(customer);try {f.recordManualPayment();}finally{f.releaseManualCounter();}}catch(InterruptedException e){Thread.currentThread().interrupt();}},"Thread-Customer-"+id);
                threads.add(t);t.start();
            }
            awaitQueue(f,29);
            assertFalse(f.isOwnerCalled());assertFalse(f.arePaymentKiosksInService());
            Thread last=new Thread(()-> {try {f.acquireManualCounter(30);try {f.recordManualPayment();}finally{f.releaseManualCounter();}}catch(InterruptedException e){Thread.currentThread().interrupt();}},"Thread-Customer-30");
            threads.add(last);last.start();
            for(Thread t:threads){t.join(3000);assertFalse(t.isAlive());}
            f.awaitOwner();assertTrue(f.isOwnerCalled());assertEquals(2,f.getOwnerState());
            assertFalse(f.arePaymentKiosksInService());assertEquals(30,f.getManualPayments());
            assertEquals(0,f.getWaitingForPayment());
            String log=output.toString();assertEquals(1,log.split("CALLING THE OWNER!",-1).length-1);
            assertTrue(log.contains("Payment queue has 30 waiting customers"));
            assertTrue(log.contains("[Owner][Thread-Owner] Manual counter OPEN"));
        }finally{f.close();for(Thread t:threads)t.interrupt();}
    }

    @Test void fiftyCustomersCompleteCongestionWithoutExceedingCapacity() throws Exception {
        Statistics stats=new Statistics();ByteArrayOutputStream output=new ByteArrayOutputStream();
        SimulationLogger log=new SimulationLogger(new PrintStream(output));LaundryFacility f=facility(stats,log,true);
        List<Thread> threads=new ArrayList<>();
        try{
            for(int i=1;i<=50;i++){Thread t=new Thread(new Customer(i,f,stats,log),"Thread-Customer-"+i);threads.add(t);t.start();}
            for(Thread t:threads){t.join(5000);assertFalse(t.isAlive());}
            assertEquals(50,stats.getTotalCustomersServed());assertEquals(50,f.getManualPayments());
            assertTrue(stats.getMaxWashers()<=6);assertTrue(stats.getMaxDryers()<=4);
            assertEquals(0,stats.getCurrentWashers());assertEquals(0,stats.getCurrentDryers());
            assertFalse(f.arePaymentKiosksInService());
            for(String line:output.toString().split("\n"))if(line.contains("[Customer-")){
                var matcher=java.util.regex.Pattern.compile("\\[Customer-(\\d+)\\]\\[Thread-Customer-(\\d+)\\]").matcher(line);
                assertTrue(matcher.find(),line);assertEquals(matcher.group(1),matcher.group(2),line);
            }
        }finally{f.close();for(Thread t:threads)t.interrupt();}
    }

    @Test void customerRetriesWashAndWaitsTwoSecondsBeforePaymentRetry() throws Exception {
        Statistics stats=new Statistics();SimulationLogger log=new SimulationLogger(new PrintStream(new ByteArrayOutputStream()));
        AtomicInteger washes=new AtomicInteger(),payments=new AtomicInteger();List<Long> paymentTimes=new CopyOnWriteArrayList<>();
        WashingMachine w=new WashingMachine(1,stats,log,1,1,0,1){@Override public boolean wash(int id){return washes.incrementAndGet()>1;}};
        PaymentKiosk k=new PaymentKiosk(1,stats,log,1,1,0){@Override public boolean pay(int id){paymentTimes.add(System.nanoTime());return payments.incrementAndGet()>1;}};
        LaundryFacility f=new LaundryFacility(log,List.of(w),List.of(new Dryer(1,stats,log,1,1)),List.of(k),false);
        Thread t=new Thread(new Customer(1,f,stats,log),"Thread-Customer-1");
        t.start();t.join(5000);if(t.isAlive()){t.interrupt();fail("Customer did not finish retry");}
        assertEquals(2,washes.get());assertEquals(2,payments.get());assertEquals(1,stats.getTotalCustomersServed());
        assertTrue(paymentTimes.get(1)-paymentTimes.get(0)>=TimeUnit.SECONDS.toNanos(2));
    }

    @Test void interruptedActiveCustomerReturnsMachine() throws Exception {
        Statistics stats=new Statistics();SimulationLogger log=new SimulationLogger(new PrintStream(new ByteArrayOutputStream()));
        LaundryFacility f=new LaundryFacility(log,List.of(new WashingMachine(1,stats,log,5000,5000,0,1)),List.of(new Dryer(1,stats,log,1,1)),List.of(new PaymentKiosk(1,stats,log,1,1,0)),false);
        Thread t=new Thread(new Customer(1,f,stats,log),"Thread-Customer-1");t.start();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(stats.getCurrentWashers()==0&&System.nanoTime()<deadline)Thread.sleep(2);
        assertEquals(1,stats.getCurrentWashers());t.interrupt();t.join(2000);assertFalse(t.isAlive());
        assertEquals(0,stats.getCurrentWashers());assertEquals(0,stats.getTotalCustomersServed());
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try {WashingMachine returned=executor.submit(f::acquireWasher).get(2,TimeUnit.SECONDS);f.releaseWasher(returned);}
        finally{executor.shutdownNow();}
    }

    @Test void generatorCancellationPreventsLaterArrivals() throws Exception {
        Statistics stats=new Statistics();SimulationLogger log=new SimulationLogger(new PrintStream(new ByteArrayOutputStream()));
        LaundryFacility f=facility(stats,log,false);CustomerGenerator gen=new CustomerGenerator(f,stats,log);
        Thread t=new Thread(gen);t.start();gen.interruptAll();t.interrupt();t.join(2000);gen.awaitAllCustomers();
        assertFalse(t.isAlive());int count=gen.getNumberOfCustomersCreated();Thread.sleep(20);
        assertEquals(count,gen.getNumberOfCustomersCreated());assertTrue(count<50);
    }
}
