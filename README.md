# Smart Laundry Simulation

Java 17+ concurrent laundry simulation with a cream-and-sage Swing floor plan.
Six washing machines, four dryers, two payment kiosks and 50 customer threads.
No Maven installation or online dependencies are needed for the scripts below.

## Run on macOS

From this project folder:

```bash
bash run.sh
bash run.sh --normal
bash run.sh --congested
bash run.sh --console
bash run.sh --console --congested
bash test.sh
```

The first command opens the GUI. Select Normal or Congested and press Start simulation.
Stop cancels the current run; wait for the controls to re-enable before starting again.
The table retains completed customers, while the floor shows active customers and capped queue illustrations.
Queue counters are authoritative when more people are waiting than fit on the floor.

## Visual design

The approved cream/sage concept is implemented as a working Swing application.
An included generated appliance sprite supplies realistic metallic machine bodies; live clothes,
people, plants, floor tiles, paths and status overlays are painted with Java2D.
The artwork is illustrative and intentionally differs in detail from the generated concept image.
Machine doors animate according to live cycle progress; customer labels and table states come from the simulation.
The app does not use the generated screenshot as its background.
Customers now walk between stages and advance in their visible queues, while the owner
walks to the manual counter after being called. See documentation/ANIMATION.md.

## Scenarios and assumptions

Normal: customers wash, dry and pay at a kiosk. Washers fail with probability 0.05
per washing attempt and reset for two seconds; payment attempts fail with probability
0.05 and are retried after two seconds. A 5% probability is not a fixed failure count.

Congested: both kiosks remain offline for the entire run. At exactly 30 waiting
customers, the owner is called once. After five seconds the owner opens a separate,
one-person manual cash counter. Customers perform their own one-second cash handover.
The owner thread only opens the counter; it does not execute another customer's actions.
No kiosk repairs are simulated in this scenario.

Arrivals remain random within the full 0-3 second range. The default uses 3000*U^2
milliseconds with U uniform in [0,1), favouring shorter gaps and giving a mean one-second
interarrival time. The brief does not prescribe a probability distribution; this
assumption targets about 60 seconds while keeping wash/dry/payment durations intact.
For uniform 0-3 second arrivals, compile via run.sh, then use:

```bash
java -Dlaundry.uniformArrivals=true -cp build/classes laundromat.Main --console
```

Runs finish only after all 50 customers finish, so duration is approximate rather than
an enforced deadline. Congestion takes longer. Uniform arrivals generally take longer too.

## Included material

- src/main/java/laundromat: complete source files
- src/test/java/laundromat: 25 JUnit tests
- tools: standalone JUnit runtime for offline testing
- documentation/IMPLEMENTATION.md: requirement mapping, concurrency, snippets, testing, limitations
- documentation/ANIMATION.md: motion design and animation checks
- documentation/VIDEO_GUIDE.md: recording outline (maximum five minutes per person)
- documentation/evidence: recorded test and simulation logs

The explanation video must be recorded by the student. Record and explain your actual code,
results and contributions, using the supplied guide as a starting point.


---

## Copyright & Usage

**Copyright © 2026 Suman Upreti. All Rights Reserved.**

Smart Laundry Simulation is publicly available as an
educational reference and portfolio project.

Visitors may study its architecture, concurrency model,
thread synchronization, and simulation design.

Unauthorized redistribution, substantial reuse of the
original implementation, commercial exploitation, or
claiming the work as one's own is not permitted, subject
to applicable law and GitHub's Terms of Service.

**This project is not released under an open-source license.**

See [COPYRIGHT.md](COPYRIGHT.md) for further details.
