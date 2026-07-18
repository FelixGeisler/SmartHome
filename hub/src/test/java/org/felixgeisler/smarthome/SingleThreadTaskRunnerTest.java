package org.felixgeisler.smarthome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SingleThreadTaskRunnerTest {

  @DisplayName("a task runs on the runner's own named daemon thread")
  @Test
  void execute_runsOnOwnNamedDaemonThread() throws InterruptedException {
    SingleThreadTaskRunner runner = SingleThreadTaskRunner.queueing("test-runner", 1, "dropped");
    AtomicReference<Thread> taskThread = new AtomicReference<>();
    CountDownLatch done = new CountDownLatch(1);

    runner.execute(
        () -> {
          taskThread.set(Thread.currentThread());
          done.countDown();
        });

    assertTrue(done.await(5, TimeUnit.SECONDS));
    assertEquals("test-runner", taskThread.get().getName());
    assertTrue(taskThread.get().isDaemon());
    runner.shutdown();
  }

  @DisplayName("a queueing runner runs a queued task once the running one finishes")
  @Test
  void queueing_runsQueuedTaskAfterRunningOne() throws InterruptedException {
    SingleThreadTaskRunner runner = SingleThreadTaskRunner.queueing("test-queue", 1, "dropped");
    CountDownLatch firstStarted = new CountDownLatch(1);
    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch secondDone = new CountDownLatch(1);
    runner.execute(
        () -> {
          firstStarted.countDown();
          await(gate);
        });
    assertTrue(firstStarted.await(5, TimeUnit.SECONDS));

    runner.execute(secondDone::countDown);
    gate.countDown();

    assertTrue(secondDone.await(5, TimeUnit.SECONDS));
    runner.shutdown();
  }

  @DisplayName("a skipping runner drops a task submitted while one is still running")
  @Test
  void skipping_dropsTaskWhileOneIsRunning() throws InterruptedException {
    SingleThreadTaskRunner runner = SingleThreadTaskRunner.skipping("test-skip", "dropped");
    CountDownLatch firstStarted = new CountDownLatch(1);
    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch firstDone = new CountDownLatch(1);
    AtomicBoolean secondRan = new AtomicBoolean(false);
    runner.execute(
        () -> {
          firstStarted.countDown();
          await(gate);
          firstDone.countDown();
        });
    assertTrue(firstStarted.await(5, TimeUnit.SECONDS));

    // Rejected synchronously: the single thread is busy and a skipping runner has no queue.
    runner.execute(() -> secondRan.set(true));
    gate.countDown();

    assertTrue(firstDone.await(5, TimeUnit.SECONDS));
    assertFalse(secondRan.get());
    runner.shutdown();
  }

  @DisplayName("a task submitted after shutdown is dropped instead of run")
  @Test
  void shutdown_dropsLaterTasks() {
    SingleThreadTaskRunner runner = SingleThreadTaskRunner.queueing("test-shutdown", 1, "dropped");
    AtomicBoolean ran = new AtomicBoolean(false);
    runner.shutdown();

    runner.execute(() -> ran.set(true));

    assertFalse(ran.get());
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting inside a test task");
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(ex);
    }
  }
}
