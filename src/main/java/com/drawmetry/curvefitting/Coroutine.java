/*
 * Copyright (c) 2011 by Erik Colban. All Rights Reserved.
 */
package com.drawmetry.curvefitting;

/**
 * The Coroutine class allows a thread (the "invoking thread") to start the
 * execution of the {@link #execute()} method on a "coroutine" thread. The
 * invoker does this by calling the {@link #attach()} method on the
 * <code>Coroutine</code> object. This method blocks until the coroutine
 * eventually yields control back to the invoker by calling {@link #detach()} or
 * when the
 * <code>attach()</code> method returns. When this happens, the invoker resumes
 * execution where it left off. 
 *
 * <p> In turn, the
 * <code>detach()</code> method blocks until the invoker yields control back to
 * the coroutine thread by calling {@link #reattach()}. When this happens the
 * coroutine resumes where it left off (i.e., immediately after the
 * <code>detach()</code> statement). The
 * <code>reattach()</code> method blocks until the coroutine yields control back
 * to the invoker again by calling
 * <code>detach()</code> again. Control is passed back and forth between the
 * invoker and the coroutine by the invoker calling
 * <code>reattach()</code> and the coroutine calling
 * <code>detach()</code>.
 *
 * <p> The
 * <code>reattach()</code> method returns a boolean which is normally
 * <code>true</code> unless the
 * <code>execute()</code> method has already returned. Shortly after the
 * <code>execute()</code> method returns, the coroutine thread dies. Calling
 * <code>reattach()</code> one or more times after the execute method has
 * returned has no effect; the method returns immediately with the value
 * <code>false</code>.
 *
 * <p> The last time that the invoker passes control to the coroutine, the
 * invoker should call {@link #cancel()}. The coroutine can and should check
 * whether it has been canceled by calling {@link #isCancelled()} and, if so,
 * terminate gracefully by returning from the
 * <code>execute()</code> method. The
 * <code>cancel()</code> method blocks until the coroutine thread is dead.
 * Calling
 * <code>cancel()</code> after the coroutine thread is dead, has no effect. An
 * example of the usage of this class is given by
 * <code>IterableFile</code>.
 *
 * @see IterableFile
 *
 *
 * @author Erik
 */
public abstract class Coroutine extends Thread {

    private static class Trap {

        private boolean toggle = false;

        synchronized void entrap() throws InterruptedException {
            boolean v = toggle = !toggle;
            notify();
            while (v == toggle) {
                wait();
            }
        }

        synchronized void release() {
            toggle = !toggle;
            notify();
        }
    }
    private Trap trap = new Trap();
    private volatile boolean running = true; //use to signal that the execute() method has returned
    private volatile boolean cancelled = false; // used to signal to the coroutine that its execute method should return

    /**
     * Starts the Coroutine thread. Called from the invoking thread.
     */
    public final void attach() {
        try {
            start(); // start the coroutine thread
            trap.entrap(); // ... and wait for the control to be handed back.
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Runs on the coroutine thread.
     */
    @Override
    public final void run() {
        try {
            execute(); //
            running = false;
            trap.release(); // Hand control over to invoking thread
        } catch (InterruptedException ex) {
            running = false;
        }
    }

    /**
     * The method to be executed on the coroutine thread.
     *
     * @throws InterruptedException if the coroutine thread is interrupted.
     */
    protected abstract void execute() throws InterruptedException;

    /**
     * Called from the coroutine thread. Gives control back to the invoking
     * thread.
     *
     * @throws InterruptedException
     */
    protected final void detach() throws InterruptedException {
        trap.entrap();
    }

    /**
     * Called from the invoking thread. Yields to the coroutine.
     *
     * @return false if the
     * <code>execute()</code> method has returned, true otherwise.
     * @throws InterruptedException
     */
    public final boolean reattach() throws InterruptedException {
        if (running) {
            trap.entrap();
        }
        return running;
    }

    /**
     * Checks if the coroutine has been stopped by the invoker. Should therefore
     * be called immediately after the invoker yields control to the coroutine,
     * i.e. after {@link #detach()}.
     *
     * @return true if the coroutine has been canceled.
     */
    public final boolean isCancelled() {
        return cancelled;
    }

    /**
     * Called from the invoker at the last time it passes control to the
     * coroutine. This method blocks until the coroutine is dead.
     *
     * @throws InterruptedException
     */
    public final void cancel() throws InterruptedException {
        cancelled = true;
        reattach(); // allows the coroutine to do some housekeeping before dying
        join(); // wait for the coroutine to die
    }
}
