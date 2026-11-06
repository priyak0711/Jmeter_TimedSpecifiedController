# Timed Specified Controller (JMeter plugin)

A logic controller that runs its children based on **elapsed test time**.

## Options

| Option | Meaning |
|---|---|
| Run once at test start (check box) | Children run once, in the first iteration after the test starts (same as the Once Only Controller). Combines with the timed run below |
| Interval + unit | Test time, in Seconds or Minutes (decimals allowed, e.g. `0.5` seconds) |
| Then run once | After the start run (if ticked), children run once more when the interval has elapsed |
| Then run repeatedly | After the start run (if ticked), children run every time the interval elapses |
| Global schedule | Unchecked (default): each thread follows the schedule on its own. Checked: one shared schedule; only one thread runs the children for the start run and for each due time |

Example: tick "Run once at test start", interval `2` Minutes, "Then run once" - the APIs hit when the test starts and once more after 2 minutes.

## Important: keep a sampler beside the controller

The controller is only checked when a thread loops past it. If it is the only thing in an infinite-loop Thread Group, every iteration is empty and JMeter overflows its stack (`StackOverflowError`). Put a pacing sampler next to it:

    Thread Group (loop count: Infinite, or a duration)
    ├─ Flow Control Action   (Pause, e.g. 100 ms)
    ├─ Timed Specified Controller   (0.5 Seconds, Run repeatedly)
    │    └─ HTTP Request
    └─ View Results Tree

The actual run time is the first loop iteration at or after the due time, so the pause length is also the timing resolution. For a 0.5 s interval use a pause of 50-100 ms.

## Build

Requires JDK 17+ (run JMeter 5.6.3 on Java 8-21) and Maven.

    mvn clean package

Jar: `target/jmeter-timed-specified-controller-1.0.0.jar`

## Install

1. Copy the jar into `<JMETER_HOME>/lib/ext/` and restart JMeter.
2. Right-click a Thread Group → **Add → Logic Controller → Timed Specified Controller**.
