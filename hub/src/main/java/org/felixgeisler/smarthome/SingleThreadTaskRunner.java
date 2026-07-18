package org.felixgeisler.smarthome;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A named, daemon, single background thread that runs tasks off the caller's thread, dropping work
 * with a warning instead of blocking when the thread cannot keep up.
 *
 * <p>Shared by the pollers, the automation engine, and the live-update broadcaster, which all need
 * the same shape: work must never run on (or block) the publisher's or scheduler's thread.
 */
public final class SingleThreadTaskRunner implements Executor {

  private static final Logger log = LoggerFactory.getLogger(SingleThreadTaskRunner.class);

  private final ThreadPoolExecutor pool;

  private SingleThreadTaskRunner(
      String threadName, BlockingQueue<Runnable> queue, String dropWarning) {
    this.pool =
        new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            queue,
            runnable -> {
              Thread thread = new Thread(runnable, threadName);
              thread.setDaemon(true);
              return thread;
            },
            (dropped, executor) -> log.warn(dropWarning));
  }

  /**
   * Creates a runner that queues up to {@code capacity} tasks behind the running one and drops
   * further tasks with the warning.
   *
   * @param threadName the background thread's name
   * @param capacity how many tasks may wait in the queue
   * @param dropWarning the warning logged when a task is dropped
   * @return the runner
   */
  public static SingleThreadTaskRunner queueing(
      String threadName, int capacity, String dropWarning) {
    return new SingleThreadTaskRunner(threadName, new LinkedBlockingQueue<>(capacity), dropWarning);
  }

  /**
   * Creates a runner without a queue: a task submitted while one is still running is dropped with
   * the warning.
   *
   * @param threadName the background thread's name
   * @param dropWarning the warning logged when a task is dropped
   * @return the runner
   */
  public static SingleThreadTaskRunner skipping(String threadName, String dropWarning) {
    return new SingleThreadTaskRunner(threadName, new SynchronousQueue<>(), dropWarning);
  }

  /**
   * Runs or queues the task on the background thread; when the runner cannot accept it, the task is
   * dropped with the configured warning.
   *
   * @param task the task to run
   */
  @Override
  public void execute(Runnable task) {
    pool.execute(task);
  }

  /**
   * Stops the runner: new tasks are dropped, already queued ones still run, the caller never waits
   * on the background thread.
   */
  public void shutdown() {
    pool.shutdown();
  }
}
