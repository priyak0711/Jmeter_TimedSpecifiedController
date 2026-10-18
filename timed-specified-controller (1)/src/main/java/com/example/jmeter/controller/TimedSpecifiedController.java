package com.example.jmeter.controller;

import java.io.Serializable;

import org.apache.jmeter.control.GenericController;
import org.apache.jmeter.control.NextIsNullException;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.threads.JMeterContextService;

/**
 * Timed Specified Controller.
 *
 * Runs its children based on elapsed test time. Two modes:
 *  - ONCE (default): children run exactly once, the first time the elapsed test
 *    time reaches the configured interval (like a time-triggered Once Only Controller).
 *  - REPEAT: children run every time the configured interval has elapsed.
 *
 * Scope:
 *  - per thread (default): every thread keeps its own schedule.
 *  - global: one schedule for the whole test; only one thread runs the children
 *    for each due time.
 *
 * Note: the controller is only evaluated when a thread loops past it, so keep
 * a sampler (for example a Flow Control Action pause) beside it in the Thread
 * Group. The actual run time is the first loop iteration at or after the due time.
 */
public class TimedSpecifiedController extends GenericController implements Serializable {

    private static final long serialVersionUID = 2L;

    public static final String INTERVAL = "TimedSpecifiedController.interval";
    public static final String UNIT = "TimedSpecifiedController.unit";
    public static final String REPEAT = "TimedSpecifiedController.repeat";
    public static final String GLOBAL_SCOPE = "TimedSpecifiedController.globalScope";

    public static final String UNIT_SECONDS = "Seconds";
    public static final String UNIT_MINUTES = "Minutes";

    // Shared state for "global" scope.
    private static final Object GLOBAL_LOCK = new Object();
    private static long globalTestStart = -1;
    private static long globalNextDue;
    private static boolean globalFired;

    // Per-thread state (JMeter clones the controller for every thread).
    private transient boolean running;
    private transient boolean executed;
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

    public boolean isRepeat() {
        return getPropertyAsBoolean(REPEAT, false);
    }

    public void setRepeat(boolean repeat) {
        setProperty(REPEAT, repeat);
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
        boolean repeat = isRepeat();
        if (!repeat && executed) {
            return false;
        }

        long now = System.currentTimeMillis();
        long testStart = JMeterContextService.getTestStartTime();
        long interval = intervalMillis();

        if (isGlobalScope()) {
            return claimGlobal(repeat, now, testStart, interval);
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
            executed = true;
        }
        return true;
    }

    private static boolean claimGlobal(boolean repeat, long now, long testStart, long interval) {
        synchronized (GLOBAL_LOCK) {
            if (globalTestStart != testStart) {
                // new test run: reset the shared schedule
                globalTestStart = testStart;
                globalNextDue = testStart + interval;
                globalFired = false;
            }
            if (!repeat && globalFired) {
                return false;
            }
            if (now < globalNextDue) {
                return false;
            }
            if (repeat) {
                globalNextDue = advance(globalNextDue, now, interval);
            } else {
                globalFired = true;
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
