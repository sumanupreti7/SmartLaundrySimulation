# System implementation and testing

## Requirement coverage

| Requirement | Implementation | Evidence/status |
|---|---|---|
| Java | Java 17 language target; compatible with the user's Java 25 JDK | Source compilation passed |
| 6 washers / 4 dryers / 2 kiosks | SimulationConfig constants and physical resource pools | Included source and live view |
| 50 customer threads | Customer implements Runnable; CustomerGenerator starts a named Thread per customer | Both full console runs served 50 |
| Random 0-3 second arrivals | Full-range biased random arrival distribution | Assumption stated in README; optional uniform mode |
| Wash 4-6 seconds | Random millisecond durations in this interval | WashingMachine implementation |
| Dry 3-5 seconds | Random millisecond durations in this interval | Dryer implementation |
| Payment 1-2 seconds | Random kiosk duration; manual cash payment fixed at lower bound, 1 second | PaymentKiosk / Customer implementation |
| Wait for resources | Fair semaphores; no busy waiting | Resource capacity and blocking tests |
| Mutual exclusion | Each pool permit grants one physical resource; queue tracks identity | Concurrent resource tests |
| 5% washer mid-cycle failure | Half-cycle failure, 2-second reset, retry loop | Forced physical failure and customer retry tests |
| 5% kiosk failure, 2-second retry | Per-attempt probability, kiosk released before retry delay | Forced kiosk test; customer retry delay test |
| Customers served | AtomicInteger after successful payment | Full runs and tests |
| Average total customer time | AtomicLong total elapsed milliseconds / completed customers | Includes all waits/retries; final summary exact |
| Peak washer/dryer use | Atomic counters and accumulateAndGet(Math::max) | Concurrent tests and full-run summaries |
| Visible simultaneous operation | Named timestamped logs and animated floor | Runtime output and GUI |
| About 60 seconds | Biased arrivals target approximately a minute | Observed normal run: 62.54 s; no hard guarantee |
| 1-2 minute implementation run | Full-run evidence | Observed normal 62.54 s; congested 94.82 s |
| Both kiosks offline for the day | Zero kiosk permits throughout congested run | Final kiosks-in-service false; test |
| Owner called after 30 waiting | Lifecycle lock plus compareAndSet; trigger at exactly 30 | Threshold and exactly-once test |
| GUI | Swing/Java2D room, activity table, queues, events, statistics | Included GUI |
| Major entry, exit, payment events include actual thread | Logger reads Thread.currentThread().getName() | Identity test and logs |
| Student explanation video | VIDEO_GUIDE.md supplied | Student recording still required |

## Assumptions

1. One customer represents one laundry load and follows wash -> dry -> payment -> exit.
2. The washer is released before requesting a dryer, and the dryer before requesting payment.
3. A failed washer remains reserved during its two-second reset, then the customer retries with any washer.
4. A failed kiosk transaction does not charge the customer; the kiosk becomes available immediately.
5. The congested owner arrives after five seconds and opens a separate single-customer manual cash counter.
6. Manual cash handover takes one second and is performed by the customer's own thread.
7. Default interarrival times are biased within 0-3 seconds, as documented in README.
8. No customer is discarded merely to meet a wall-clock deadline.
9. GUI readings are observational snapshots; separately read counters can briefly describe slightly different instants.
10. The visible floor caps the number of drawn waiting people. Numeric queues and the table track all customers.

## Concurrency facilities

### Threads and ownership

Each customer runs its entire lifecycle in its own named thread:

```java
Thread thread = new Thread(customer, "Thread-Customer-" + id);
thread.start();
```

The generator creates and starts threads but does not wash, dry or pay for them.
The owner opens a counter; customers perform their own cash handover.
The GUI and console monitor only observe the simulation.

### Safe capacity and resource identity

```java
this.permits = new Semaphore(initialPermits, true);
permits.acquire();
T item = idleItems.poll();
```

A fair semaphore controls capacity. ConcurrentLinkedQueue tracks which physical
resource is free. Release offers the resource back before releasing its permit:

```java
idleItems.offer(item);
permits.release();
```

A semaphore is not itself a single-thread mutex when it has several permits. Here it
allows six/four/two customers at once, while the resource queue assigns each physical
machine to only one of those customers.

### Atomic statistics

```java
int nowInUse = currentWashers.incrementAndGet();
maxWashers.accumulateAndGet(nowInUse, Math::max);
totalCustomerTimeMillis.addAndGet(totalTimeMillis);
totalCustomersServed.incrementAndGet();
```

Atomic operations avoid lost updates from concurrent read-modify-write operations.
The final average is exact after all customers have joined; live averages are approximate
because the completed-count and elapsed-total atomics are read separately.

### One owner call

The payment-queue increment and threshold check share ownerLifecycle's monitor.
This ensures that the threshold event observes exactly 30, even with simultaneous arrivals.

```java
synchronized (ownerLifecycle) {
    int waiting = waitingForPayment.incrementAndGet();
    if (waiting >= SimulationConfig.OWNER_CALL_THRESHOLD
            && ownerCalled.compareAndSet(false, true)) {
        // Start the owner thread once.
    }
}
```

After the owner arrives, it releases one permit for the manual counter. That permit
is handed back by each paying customer, so manual payments are serialized.

### Cleanup and deadlock avoidance

```java
WashingMachine washer = facility.acquireWasher();
try {
    washed = washer.wash(id);
} finally {
    facility.releaseWasher(washer);
}
```

Finally blocks release resources and decrement occupancy counters during failures and
interruptions. Customers hold at most one resource type at once, avoiding circular
resource waits. join() waits for all customer threads before printing final statistics.
Stopping interrupts both blocked and active customers. Generator creation and cancellation
share a lock, preventing an un-interrupted customer from starting after cancellation.
The owner is interrupted and joined before a session finishes.

### Logging, visibility and GUI safety

ReentrantLock serializes printing of whole log lines; each line includes the actual
executing thread. Volatile fields publish machine state to observers. An immutable Activity record, stored
in a volatile reference, publishes customer stage/state together.
CopyOnWriteArrayList permits safe iteration over customers while arrivals are added.
Swing components are changed only on the EDT, via invokeLater or a Swing Timer.
A separate coordinator joins simulation threads, keeping the GUI responsive.
Session numbers reject queued event text from an older run after restarting.
Thread.sleep simulates elapsed work and retry delays; semaphores implement waiting.

## Verification

Source compiled with Java 17 target. All 25 JUnit tests passed (22 requirement tests and 3 movement checks).
A graphical smoke check also passed: customer walking, live rows/rendering, exit cleanup,
owner entrance/arrival using a controlled queue fixture, Stop, restart, and no remaining
customer, generator, owner or coordinator threads after cancellation.
The original 17 tests cover statistics, successful/failed machine operations, concurrent
capacity, customer completion and interruption. Five added tests cover:

- Owner not called at 29, called exactly once at 30, kiosks remain broken.
- All 50 customers complete the congested scenario; actor/thread identities match.
- Wash retries and the two-second payment retry interval.
- Interruption during an active wash returns the physical machine.
- Cancellation prevents subsequent generator arrivals.

Fast tests deliberately use injected short durations and forced failure behavior;
full console runs separately use the real configured durations and probabilities.

| Observed metric | Normal | Congested |
|---|---:|---:|
| Customers served | 50 | 50 |
| Duration | 62.54 s | 94.82 s |
| Peak washers | 6 | 6 |
| Peak dryers | 4 | 4 |
| Manual payments | 0 | 50 |
| Kiosks still offline | No | Yes |

Random samples differ between runs. No fixed 5% failure count or exact end time is promised.

## Requirements not met / remaining deliverables

- The student must still record the narrated video and keep it within five minutes per person.
- Exactly 60 seconds is not guaranteed. The brief uses approximate timing and also permits 1-2 minutes;
  the default targets about a minute, while allowing every customer to finish.
- The Java2D illustration recreates the approved design direction, not every pixel of the generated image.
- The GUI has been built for a desktop screen; smaller screens may require enlarging the window.
- The GUI needs a graphical desktop; console mode also works in a headless environment.

