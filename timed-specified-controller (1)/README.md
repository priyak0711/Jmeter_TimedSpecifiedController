# Timed Specified Controller (JMeter plugin)

A logic controller that runs its children based on **elapsed test time**.

## Options

| Option | Meaning |
|---|---|
| Interval + unit | Test time, in Seconds or Minutes (decimals allowed, e.g. `0.5` seconds) |
| Run once | Children run once, when the interval has elapsed (like a time-triggered Once Only Controller) |
| Run repeatedly | Children run every time the interval elapses (first run after one interval) |
| Global schedule | Unchecked (default): each thread keeps its own schedule. Checked: one shared schedule; only one thread runs the children per due time |

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
