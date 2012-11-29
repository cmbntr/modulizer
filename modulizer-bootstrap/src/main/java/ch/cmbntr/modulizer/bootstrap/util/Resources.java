package ch.cmbntr.modulizer.bootstrap.util;

import static ch.cmbntr.modulizer.bootstrap.util.ModulizerLog.log;
import static java.lang.Math.max;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class Resources {

  private static final ThreadFactory THREAD_FACTORY = new ModulizerThreadFactory();

  private static WeakReference<Pool> pool;

  private Resources() {
    super();
  }

  public static Thread newThread(final String threadName, final Runnable runnable) {
    final Thread t = THREAD_FACTORY.newThread(runnable);
    t.setName(threadName);
    return t;
  }

  public static synchronized Pool getPoolHandle() {
    Pool p = pool == null ? null : pool.get();
    if (p == null) {
      p = new BasicPool();
      pool = new WeakReference<Pool>(p);
      log("init resources pool: %s", p);
    }
    return p;
  }

  public static synchronized void dispose(final Pool handle) {
    pool = null;
    log("dispose pool: %s", handle);
    if (handle instanceof BasicPool) {
      ((BasicPool) handle).dispose();
    }
  }

  public static interface Pool {

    public ExecutorService aquireExec();

    public void releaseExec(ExecutorService svc);

    public ScheduledExecutorService aquireBlockableExec();

    public void releaseBlockableExec(ScheduledExecutorService svc);

    public ByteBuffer aquireBuffer();

    public void releaseBuffer(ByteBuffer buf);

    public MessageDigest aquireDigest();

    public void releaseDigest(MessageDigest digest);

  }

  private static class BasicPool implements Pool {

    private static final int POOL_SIZE = max(2, Runtime.getRuntime().availableProcessors());

    private static final int BUFFER_SIZE = 32 * 1024;

    private final BlockingDeque<ByteBuffer> buffers = new LinkedBlockingDeque<ByteBuffer>(POOL_SIZE);

    private final BlockingDeque<MessageDigest> digests = new LinkedBlockingDeque<MessageDigest>(POOL_SIZE);

    private ThreadPoolExecutor exec;

    private ScheduledThreadPoolExecutor blockableExec;

    private static ThreadPoolExecutor buildNonBlockableExecutor() {
      final BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>();
      final ThreadPoolExecutor tpe = new ThreadPoolExecutor(POOL_SIZE, POOL_SIZE, 15L, TimeUnit.SECONDS, workQueue,
          THREAD_FACTORY);
      tpe.allowCoreThreadTimeOut(true);
      tpe.prestartCoreThread();
      return tpe;
    }

    private static ScheduledThreadPoolExecutor buildBlockableExecutor() {
      final ScheduledThreadPoolExecutor stpe = new ScheduledThreadPoolExecutor(POOL_SIZE, THREAD_FACTORY);
      stpe.setKeepAliveTime(15L, TimeUnit.SECONDS);
      stpe.allowCoreThreadTimeOut(true);
      return stpe;
    }

    private synchronized void dispose() {
      if (this.exec != null) {
        this.exec.shutdown();
      }
      if (this.blockableExec != null) {
        this.blockableExec.shutdown();
      }
      this.buffers.clear();
      this.digests.clear();
    }

    @Override
    public synchronized ExecutorService aquireExec() {
      if (this.exec == null) {
        this.exec = buildNonBlockableExecutor();
      }
      return Executors.unconfigurableExecutorService(this.exec);
    }

    @Override
    public void releaseExec(final ExecutorService svc) {
      // no op
    }

    @Override
    public synchronized ScheduledExecutorService aquireBlockableExec() {
      if (this.blockableExec == null) {
        this.blockableExec = buildBlockableExecutor();
      }
      return Executors.unconfigurableScheduledExecutorService(this.blockableExec);
    }

    @Override
    public void releaseBlockableExec(final ScheduledExecutorService svc) {
      // no op
    }

    @Override
    public ByteBuffer aquireBuffer() {
      final ByteBuffer b = pop(this.buffers);
      if (b == null) {
        return ByteBuffer.allocate(BUFFER_SIZE);
      } else {
        b.clear();
        return b;
      }
    }

    @Override
    public void releaseBuffer(final ByteBuffer buf) {
      push(this.buffers, buf);
    }

    @Override
    public MessageDigest aquireDigest() {
      final MessageDigest d = pop(this.digests);
      if (d == null) {
        try {
          return MessageDigest.getInstance("SHA-1");
        } catch (final NoSuchAlgorithmException e) {
          throw new RuntimeException(e);
        }
      } else {
        d.reset();
        return d;
      }
    }

    @Override
    public void releaseDigest(final MessageDigest digest) {
      push(this.digests, digest);
    }

    private static <T> T pop(final BlockingDeque<T> lifo) {
      return lifo.pollFirst();
    }

    private static <T> boolean push(final BlockingDeque<T> lifo, final T item) {
      return lifo.offerFirst(item);
    }

    @Override
    public String toString() {
      return String.format("%s[buffers=%d, digests=%d, nonblockableThreads=%s, blockableThreads=%s]", super.toString(),
          this.buffers.size(), this.digests.size(), threadCount(this.exec), threadCount(this.blockableExec));
    }

    private static String threadCount(final ThreadPoolExecutor e) {
      return e == null ? "NA" : e.getActiveCount() + "/" + e.getLargestPoolSize();
    }

  }

  public static void delay(final long millisDelay, final Runnable work) {
    delay(millisDelay, getPoolHandle(), work);
  }

  public static void delay(final long millisDelay, final Pool pool, final Runnable work) {
    final ScheduledExecutorService exec = pool.aquireBlockableExec();
    try {
      exec.schedule(work, millisDelay, TimeUnit.MILLISECONDS);
    } finally {
      pool.releaseBlockableExec(exec);
    }
  }

  public static <T> Future<T> submit(final Callable<T> work) {
    return submit(getPoolHandle(), work);
  }

  public static <T> Future<T> submit(final Pool pool, final Callable<T> work) {
    final ExecutorService exec = pool.aquireExec();
    try {
      return exec.submit(work);
    } finally {
      pool.releaseExec(exec);
    }
  }

  public static void execute(final Runnable work) {
    execute(getPoolHandle(), work);
  }

  public static void execute(final Pool pool, final Runnable work) {
    final ExecutorService exec = pool.aquireExec();
    try {
      exec.execute(work);
    } finally {
      pool.releaseExec(exec);
    }
  }

  public static <T> T get(final Future<T> holder, final String errorMsg) {
    try {
      return holder.get(1L, TimeUnit.HOURS);
    } catch (final InterruptedException e) {
      Thread.interrupted();
      throw failGet(errorMsg, e);
    } catch (final ExecutionException e) {
      throw failGet(errorMsg, e.getCause());
    } catch (final TimeoutException e) {
      throw failGet(errorMsg, e);
    }
  }

  private static RuntimeException failGet(final String msg, final Throwable cause) {
    throw new RuntimeException(msg, cause);
  }

  private static final class ModulizerThreadFactory implements ThreadFactory {

    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger(1);

    private static final ThreadGroup THREAD_GROUP = new ThreadGroup("modulizer threads");

    @Override
    public Thread newThread(final Runnable r) {
      final Thread t = new Thread(THREAD_GROUP, r, "modulizer thread - " + THREAD_NUMBER.getAndIncrement());
      if (t.isDaemon()) {
        t.setDaemon(false);
      }
      if (t.getPriority() != Thread.NORM_PRIORITY) {
        t.setPriority(Thread.NORM_PRIORITY);
      }
      return t;
    }
  };

}
