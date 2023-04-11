package com.shyamanand.singlethreadedscheduler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class SingleThreadedScheduler extends Thread {

    private static final Logger logger = LogManager.getLogger(SingleThreadedScheduler.class);
    private final PriorityQueue<Work> workQueue = new PriorityQueue<>(
            Comparator.comparing(Work::getStartAt));
    private final Long queueCapacity;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition queueFullCondition = lock.newCondition();
    private final Condition queueNotEmpty = lock.newCondition();

    private static final Work POISON_PILL = new PoisonPill();

    static {
        POISON_PILL.setName("POISON_PILL");
    }

    private SchedulerState schedulerState = SchedulerState.NEW;
    private boolean queueFull = false;

    public SingleThreadedScheduler() {
        super("Scheduler");
        String configFile = Paths.get("schedulerwithdelay/config/config").toAbsolutePath().toString();
        logger.info("Loading config from " + configFile);
        Properties properties = new Properties();
        try {
            properties.load(Files.newInputStream(Paths.get(configFile)));
            queueCapacity = Long.valueOf(properties.getProperty("capacity"));
            logger.info("queueCapacity=" + queueCapacity);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void scheduleWithDelay(Runnable r, String name, long delay) {
        try {
            lock.lock();
            while (workQueue.size() >= queueCapacity) {
                queueFull = true;
                try {
                    logger.info("Queue full, waiting.");
                    queueFullCondition.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (this.schedulerState != SchedulerState.RUNNING) {
                throw new IllegalStateException("Scheduler not running (" + this.schedulerState + ").");
            }
            Work work = new Work(r, delay);
            work.setName(name);
            workQueue.add(work);
            logger.info("Added: " + work);

            queueNotEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public String getQueueContents() {
        return workQueue.toString();
    }

    public void shutdown() {
        setShuttingDown();
        workQueue.add(POISON_PILL);
    }

    public void run() {
        try {
            schedule();
        } finally {
            setShutDown();
        }
    }

    private void schedule() {
        setRunning();

        while (true) {
            try {
                lock.lock();
                logger.info("locked");

                while (workQueue.isEmpty()) {
                    logger.info("Queue empty, waiting.");
                    queueNotEmpty.await();
                }

                logger.info("Polling work queue: " + workQueue);
                Work work = workQueue.poll();

                if (queueFull) {
                    queueFull = false;
                    queueFullCondition.signalAll();
                }

                if (work == null) {
                    logger.info("received null work.");
                    continue;
                }

                if (work == POISON_PILL) {
                    break;
                }

                logger.info("Found " + work);
                execute(work);
            } catch (InterruptedException e) {
                logger.info("Interrupted!");
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        }
    }

    private void execute(Work work) throws InterruptedException {
        long now = System.currentTimeMillis();
        long delay = Math.max(0, work.getStartAt() - now);

        logger.info(work.getName() + " scheduled to start in " + delay + " ms.");
        boolean wasInterrupted = queueNotEmpty.await(delay, TimeUnit.MILLISECONDS);

        if (wasInterrupted) {
            logger.info("New work was added. Skipping " + work.getName() + " for later");
            workQueue.add(work);
        } else {
            logger.info(work + " started at " + System.currentTimeMillis() +
                    "(" + (System.currentTimeMillis() - work.startAt) + " ms)");
            work.start();
        }
    }

    private void setShutDown() {
        this.schedulerState = SchedulerState.SHUTDOWN;
    }

    private void setShuttingDown() {
        this.schedulerState = SchedulerState.SHUTTING_DOWN;
    }

    private void setRunning() {
        this.schedulerState = SchedulerState.RUNNING;
    }

    public boolean isRunning() {
        return this.schedulerState == SchedulerState.RUNNING;
    }

    public SchedulerState getSchedulerState() {
        return this.schedulerState;
    }

    private static class Work extends Thread {
        private final Long startAt;

        private Work(Runnable r, Long delay) {
            super(r);
            this.startAt = System.currentTimeMillis() + delay;
        }

        private Work(Runnable r) {
            super(r);
            this.startAt = System.currentTimeMillis();
        }

        private Long getStartAt() {
            return startAt;
        }

        public String toString() {
            return "Worker[" + getName() + ", " + startAt + "]";
        }
    }

    private static final class PoisonPill extends Work {
        private PoisonPill() {
            super(null);
        }
    }
}