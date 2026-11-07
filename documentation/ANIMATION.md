# Customer movement and advancing queues

The floor-plan update adds three features: customers walk between stages, illustrated
queues advance as customers leave them, and the owner walks in after the queue trigger.
It also animates each customer's departure rather than removing the figure immediately.

## How it works

Customer publishes an immutable Activity snapshot through one volatile reference.
Stage, state and the time that activity began are therefore observed together by the GUI.
Only the customer's own simulation thread publishes those activities.

The existing 100 ms Swing refresh reads customer and facility state and assigns destinations:
washer, dryer, payment kiosk, manual counter, visible queue position, or exit.
A separate 33 ms Swing timer advances WalkMotion objects using elapsed time. Both timers
run on the EDT. No animation code sleeps or updates simulation counters or resources.

WalkMotion routes longer journeys through the central aisle. Retargeting starts from
the customer's current drawn position, so an advancing queue does not cause a jump.
Figures are painted in floor-depth order, with a small walking gait while moving.
The owner enters after getOwnerState changes to 'on the way' and remains at the manual
counter after it opens. Starting a new run clears all previous animation state.

## Simulation behavior

Resource counts, wash/dry/payment durations, 5% failure rates, two-second payment retry,
customer thread ownership and the owner trigger remain unchanged by the animation update.
The previously implemented normal/congested scenarios and arrival distribution are preserved.
Kiosks still remain offline for the day during congestion; the owner opens the manual counter.

Visual motion follows real events and can briefly trail the exact activity shown in the table.
Walking is an illustrative transition, not an extra timed stage in the assignment model.
Live queues and statistics are authoritative. Visible queue order uses published waiting-entry
timestamps; actual access remains controlled by the facility's fair semaphores.
The floor caps visible waiting figures (6 washer, 4 dryer, 6 payment) and shows overflow badges.

## Verification

All 25 tests passed: the existing 22 requirement tests plus three movement checks for
no destination overshoot, retargeting an advancing queue without a teleport, and aisle routing.
A graphical smoke check verified changing customer positions, live rows, departure cleanup,
owner entrance/arrival, restart, cancellation and no surviving simulation threads.
The owner smoke check uses a controlled 30-request queue fixture, rather than waiting for
random arrivals. It tests the actual owner trigger and five-second owner response.
The previous full real-time scenario evidence remains applicable to the unchanged core timings.

Run all tests from the project folder:

```bash
bash test.sh
```

The narrated student video is still a required separate deliverable. Demonstrate customer
movement and queue progression while explaining that service timing belongs to customer threads.

