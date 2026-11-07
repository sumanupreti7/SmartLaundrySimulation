# Five-minute recording guide

Record your own screen and voice. Explain the parts you personally understand and contributed.
Use Shift + Command + 5 on macOS and enable your microphone in Options.

| Time | Demonstration / explanation |
|---|---|
| 0:00-0:25 | Introduce Smart Laundry Simulation: Java, 50 customer threads, 6 washers, 4 dryers, 2 kiosks. |
| 0:25-1:25 | Start Normal. Point to live machines, customers, queues, event identities and statistics. Explain wash/dry/pay timing. |
| 1:25-2:00 | Show the normal result. Explain a failure/retry if one occurred; otherwise show the relevant source and forced-failure test. |
| 2:00-3:40 | Start Congested. Show both kiosks offline, growing queue, owner call at 30, then manual payments. Explain that the kiosks stay broken all day. |
| 3:40-4:35 | Show ResourcePool semaphore/queue, Customer finally blocks, atomic statistics and owner threshold guard. Explain ownership, visibility and deadlock avoidance. |
| 4:35-5:00 | Show the test summary, assumptions and remaining limitations. |

Random durations can shift the timeline. If necessary, shorten your commentary or use a clearly
labelled pre-recorded run segment; do not present accelerated footage as real-time timing evidence.
Each person should keep their own contribution explanation within the assignment's five-minute limit.

Useful commands:

```bash
bash run.sh
bash test.sh
bash run.sh --console
bash run.sh --console --congested
```

Statements to understand before presenting:

- A fair semaphore limits available resource slots and makes customers block when capacity is full.
- The idle queue identifies the actual free machine; a permit alone does not identify it.
- A customer's own thread performs entry, washing, drying, payment and exit actions.
- Finally returns resources even when work fails or is interrupted.
- Atomics protect independent counters against lost updates.
- Customers never hold two resource types at once, preventing circular resource waiting.
- The owner is called once at 30 waiting customers and opens a manual cash counter.
- The default arrival distribution favours shorter gaps within the full 0-3 second range.
- The average customer visit includes queueing and retries, not just active machine time.
- The EDT updates Swing; customer threads do not mutate UI components.
