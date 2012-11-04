package ch.cmbntr.modulizer.bootstrap;

import static ch.cmbntr.modulizer.bootstrap.Operations.defaultLoader;
import static ch.cmbntr.modulizer.bootstrap.Operations.invokeOperations;
import static ch.cmbntr.modulizer.bootstrap.util.ModulizerLog.log;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.lang.management.ManagementFactory;

public class Main {

  private static final long STARTUP_NANOS = System.nanoTime();

  private Main() {
    super();
  }

  public static void main(final String[] args) {
    BootstrapContext.ARGS.set(args);
    try {
      invokeOperations(Bootstrap.class, defaultLoader());
    } finally {
      BootstrapContext.ARGS.set(null);
      log("bootstrap time: %dms", MILLISECONDS.convert(System.nanoTime() - STARTUP_NANOS, NANOSECONDS));
      ManagementFactory.getMemoryMXBean().gc();
    }
  }
}
