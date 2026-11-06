package com.example.jmeter.controller;

import java.io.Serializable;

import org.apache.jmeter.control.GenericController;
import org.apache.jmeter.control.NextIsNullException;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.threads.JMeterContextService;

/**
 * Timed Specified Controller.
 *
 * Two independent parts that can be combined:
 *  - "Run once at test start" (check box): the children run once, in the first
 *    iteration after the test starts (same as the Once Only Controller).
 *  - Timed run (mode ONCE or REPEAT): the children run when the configured interval
 *    of test time has elapsed - once (ONCE) or every time it elapses (REPEAT).
 *
 * Scope:
 *  - per thread (default): every thread follows the schedule on its own.
 *  - global: one schedule for the whole test; only one thread runs the children
 *    for the start run and for each due time.
 *
 * Keep a sampler (for example a Flow Control Action pause) beside the controller
 * in the Thread Group, so every iteration has something to run while the timed
 * part is waiting.
 */
public class TimedSpecifiedController extends GenericController implements Serializable {

    private static final long serialVersionUID = 4L;

    public static final String INTERVAL = "TimedSpecifiedController.interval";
    public static final String UNIT = "TimedSpecifiedController.unit";
    public static final String MODE = "TimedSpecifiedController.mode";
    public static final String RUN_AT_START = "TimedSpecifiedController.runAtStart";
    /** Legacy boolean from an older version, used only when MODE is not set. */
    public static final String REPEAT = "TimedSpecifiedController.repeat";
    public static final String GLOBAL_SCOPE = "TimedSpecifiedController.globalScope";

    public static final String UNIT_SECONDS = "Seconds";
    public static final String UNIT_MINUTES = "Minutes";

    public static final String MODE_ONCE = "ONCE";
    public static final String MODE_REPEAT = "REPEAT";

    // Shared state for "global" scope.
    private static final Object GLOBAL_LOCK = new Object();
    private static long globalTestStart = -1;
    private static long globalNextDue;
    private static boolean globalTimedFired;
    private static boolean globalStartFired;

    // Per-thread state (JMeter clones the controller for every thread).
    private transient boolean running;
    private transient boolean startDone;
    private transient boolean timedDone;
    private transient long nextDue = -1;

    // ---- properties -------------------------------------------------------

    public double getInterval() {
        String raw = getPropertyAsString(INTERVAL, "1");
        try {
            double v = Double.parseDouble(raw.trim());
            return v >= 0 ? v : 1.0;
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }

    public void setInterval(double interval) {
        setProperty(INTERVAL, String.valueOf(interval));
    }

    public String getUnit() {
        String unit = getPropertyAsString(UNIT, UNIT_MINUTES);
        return UNIT_SECONDS.equalsIgnoreCase(unit) ? UNIT_SECONDS : UNIT_MINUTES;
    }

    public void setUnit(String unit) {
        setProperty(UNIT, unit);
    }

    public String getMode() {
        String mode = getPropertyAsString(MODE, "");
        if (MODE_ONCE.equals(mode) || MODE_REPEAT.equals(mode)) {
            return mode;
        }
        // older saved plans: "START" or nothing at all
        return getPropertyAsBoolean(REPEAT, false) ? MODE_REPEAT : MODE_ONCE;
    }

    public void setMode(String mode) {
        setProperty(MODE, mode);
    }

    public boolean isRunAtStart() {
        return getPropertyAsBoolean(RUN_AT_START, false)
                || "START".equals(getPropertyAsString(MODE, ""));
    }

    public void setRunAtStart(boolean runAtStart) {
        setProperty(RUN_AT_START, runAtStart);
    }

    public boolean isGlobalScope() {
        return getPropertyAsBoolean(GLOBAL_SCOPE, false);
    }

    public void setGlobalScope(boolean globalScope) {
        setProperty(GLOBAL_SCOPE, globalScope);
    }

    private long intervalMillis() {
        double factor = UNIT_SECONDS.equals(getUnit()) ? 1000d : 60_000d;
        return Math.max(1L, (long) (getInterval() * factor));
    }

    // ---- controller logic -------------------------------------------------

    @Override
    public Sampler next() {
        if (running) {
            Sampler sampler = super.next();
            if (sampler == null) {
                // children finished this run
                running = false;
            }
            return sampler;
        }

        if (!isDue()) {
            return skip();
        }

        running = true;
        Sampler sampler = super.next();
        if (sampler == null) {
            // no enabled children
            running = false;
        }
        return sampler;
    }

    private Sampler skip() {
        try {
            return nextIsNull();
        } catch (NextIsNullException e) {
            return null;
        }
    }

    /** Decides whether the children should start now, and updates the schedule if so. */
    private boolean isDue() {
        long now = System.currentTimeMillis();
        long testStart = JMeterContextService.getTestStartTime();
        boolean global = isGlobalScope();

        // 1. start run (once at the beginning of the test)
        if (isRunAtStart() && !startDone) {
            startDone = true;
            if (!global || claimGlobalStart(testStart)) {
                return true;
            }
        }

        // 2. timed run (after the interval, once or repeatedly)
        boolean repeat = MODE_REPEAT.equals(getMode());
        if (!repeat && timedDone) {
            return false;
        }
        long interval = intervalMillis();

        if (global) {
            return claimGlobalTimed(repeat, now, testStart, interval);
        }

        if (nextDue < 0) {
            nextDue = testStart + interval;
        }
        if (now < nextDue) {
            return false;
        }
        if (repeat) {
            nextDue = advance(nextDue, now, interval);
        } else {
            timedDone = true;
        }
        return true;
    }

    private static void resetGlobalIfNewTest(long testStart, long interval) {
        if (globalTestStart != testStart) {
            globalTestStart = testStart;
            globalNextDue = testStart + interval;
            globalTimedFired = false;
            globalStartFired = false;
        }
    }

    private static boolean claimGlobalStart(long testStart) {
        synchronized (GLOBAL_LOCK) {
            resetGlobalIfNewTest(testStart, Long.MAX_VALUE / 4);
            if (globalStartFired) {
                return false;
            }
            globalStartFired = true;
            return true;
        }
    }

    private boolean claimGlobalTimed(boolean repeat, long now, long testStart, long interval) {
        synchronized (GLOBAL_LOCK) {
            resetGlobalIfNewTest(testStart, interval);
            // the shared due time may have been initialised by the start claim
            if (globalNextDue > testStart + interval) {
                globalNextDue = testStart + interval;
            }
            if (!repeat && globalTimedFired) {
                return false;
            }
            if (now < globalNextDue) {
                return false;
            }
            if (repeat) {
                globalNextDue = advance(globalNextDue, now, interval);
            } else {
                globalTimedFired = true;
            }
            return true;
        }
    }

    /** Next due time: previous due + interval, but never in the past (avoids bursts after a stall). */
    private static long advance(long due, long now, long interval) {
        long next = due + interval;
        return next <= now ? now + interval : next;
    }

    @Override
    public void triggerEndOfLoop() {
        super.triggerEndOfLoop();
        running = false;
    }
}
