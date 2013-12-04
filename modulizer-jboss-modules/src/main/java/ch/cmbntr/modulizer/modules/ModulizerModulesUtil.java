package ch.cmbntr.modulizer.modules;

import static ch.cmbntr.modulizer.bootstrap.util.SystemPropertyHelper.restoreProps;
import static ch.cmbntr.modulizer.bootstrap.util.SystemPropertyHelper.snapshotProps;

import java.io.File;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.jboss.modules.LocalModuleLoader;
import org.jboss.modules.Main;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;

import ch.cmbntr.modulizer.bootstrap.util.ModulizerLog.ExceptionLogger;
import ch.cmbntr.modulizer.bootstrap.util.Resources;
import ch.cmbntr.modulizer.bootstrap.util.Resources.Pool;

public class ModulizerModulesUtil {

  private static final Class<?>[] LAUNCH_SIGNATURE = { File.class, String.class, String[].class };

  private ModulizerModulesUtil() {
    super();
  }

  public static List<Module> tryLoadModules(final boolean mandatory, final Iterable<String> moduleIdentifiers,
      final File... repoRoots) {
    final Pool pool = Resources.getPoolHandle();
    final ExecutorService exec = pool.aquireExec();
    final Properties origProps = snapshotProps();
    try {
      final LocalModuleLoader loader = new LocalModuleLoader(repoRoots);
      final List<Future<Module>> asyncModules = new LinkedList<Future<Module>>();
      for (final String moduleIdentifier : moduleIdentifiers) {
        asyncModules.add(exec.submit(new Callable<Module>() {
          @Override
          public Module call() throws Exception {
            return loader.loadModule(ModuleIdentifier.fromString(moduleIdentifier));
          }
        }));
      }

      final List<Module> modules = new ArrayList<Module>(asyncModules.size());
      for (final Future<Module> m : asyncModules) {
        try {
          modules.add(Resources.get(m, "failed to load module"));
        } catch (final RuntimeException e) {
          if (mandatory) {
            throw e;
          }
        }
      }
      return modules;
    } finally {
      restoreProps(origProps);
      pool.releaseExec(exec);
    }
  }

  public static CountDownLatch invokeModulesMain(final File modulesRepo, final String mainModule, final String... args) {
    return invokeModulesMain(mainModule, modulesRepo, mainModule, args);
  }

  public static CountDownLatch invokeModulesMain(final String appName, final File modulesRepo, final String mainModule,
      final String... args) {
    final String threadName = String.format("main[%s]", appName);
    return invokeModulesMain(threadName, ExceptionLogger.create(), modulesRepo, mainModule, args);
  }

  private static CountDownLatch invokeModulesMain(final String threadName,
      final UncaughtExceptionHandler exceptionHandler, final File modulesRepo, final String mainModule,
      final String... args) {
    final CountDownLatch running = new CountDownLatch(1);
    final CountDownLatch done = new CountDownLatch(1);
    startAndAwaitRunning(createThread(threadName, running, done, exceptionHandler, modulesRepo, mainModule, args),
        running);
    return done;
  }

  private static Thread createThread(final String threadName, final CountDownLatch running, final CountDownLatch done,
      final UncaughtExceptionHandler exceptionHandler, final File modulesRepo, final String mainModule,
      final String... args) {
    final Thread t = Resources.newThread(threadName, MainRunner.create(running, done, modulesRepo, mainModule, args));
    if (exceptionHandler != null) {
      t.setUncaughtExceptionHandler(exceptionHandler);
    }
    return t;
  }

  private static void startAndAwaitRunning(final Thread t, final CountDownLatch running) {
    t.start();
    try {
      running.await();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public static void invokeModulesMainAndWait(final File modulesRepo, final String mainModule, final String... args) {
    try {
      Main.main(assembleRunArguments(modulesRepo, mainModule, args));
    } catch (final Throwable e) {
      if (e instanceof RuntimeException) {
        throw (RuntimeException) e;
      }
      throw new RuntimeException(e);
    }
  }

  private static String[] assembleRunArguments(final File modulesRepo, final String mainModule, final String... args)
      throws IOException {
    final String repoPath = modulesRepo.getCanonicalFile().getAbsolutePath();
    final String[] basicArgs = { "-modulepath", repoPath, mainModule };
    if (args != null) {
      final int argsCount = args.length;
      if (args.length > 0) {
        final int basicCount = basicArgs.length;
        final String[] fullArgs = new String[basicCount + argsCount];
        System.arraycopy(basicArgs, 0, fullArgs, 0, basicCount);
        System.arraycopy(args, 0, fullArgs, basicCount, argsCount);
        return fullArgs;
      }
    }
    return basicArgs;
  }

  public static Method getLaunchMethod(final ClassLoader loader, final boolean wait) throws NoSuchMethodException,
      SecurityException, ClassNotFoundException {
    final Class<?> mmu = loader.loadClass(ModulizerModulesUtil.class.getName());
    return mmu.getMethod(wait ? "invokeModulesMainAndWait" : "invokeModulesMain", LAUNCH_SIGNATURE);
  }

  public static void main(final String[] args) throws InterruptedException {
    if (args == null || args.length < 2) {
      System.err.format("Usage: java %s repoPath mainModule [args...]", ModulizerModulesUtil.class.getName());
      System.exit(1);
    } else {

      final File repoPath = new File(args[0]);
      final String mainModule = args[1];
      String[] moduleArgs = null;
      if (args.length > 2) {
        moduleArgs = new String[args.length - 2];
        System.arraycopy(args, 0, moduleArgs, 0, moduleArgs.length);
      }
      final CountDownLatch done = invokeModulesMain(mainModule, repoPath, mainModule, moduleArgs);
      done.await();
      System.exit(0);
    }
  }

  private static final class MainRunner implements Runnable {

    private final CountDownLatch running;
    private final CountDownLatch done;
    private final File modulesRepo;
    private final String mainModule;
    private final String[] args;

    private MainRunner(final CountDownLatch running, final CountDownLatch done, final File modulesRepo,
        final String mainModule, final String... args) {
      this.running = running;
      this.done = done;
      this.modulesRepo = modulesRepo;
      this.mainModule = mainModule;
      this.args = args;
    }

    private static Runnable create(final CountDownLatch running, final CountDownLatch done, final File modulesRepo,
        final String mainModule, final String... args) {
      return new MainRunner(running, done, modulesRepo, mainModule, args);
    }

    @Override
    public void run() {
      try {
        this.running.countDown();
        invokeModulesMainAndWait(this.modulesRepo, this.mainModule, this.args);
      } finally {
        this.done.countDown();
      }
    }
  }

}
