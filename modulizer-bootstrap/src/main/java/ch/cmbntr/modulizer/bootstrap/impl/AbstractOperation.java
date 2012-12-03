package ch.cmbntr.modulizer.bootstrap.impl;

import static ch.cmbntr.modulizer.bootstrap.Operations.invokeOperations;
import static ch.cmbntr.modulizer.bootstrap.util.Resources.submit;

import java.io.File;
import java.util.concurrent.Future;

import ch.cmbntr.modulizer.bootstrap.BootstrapContext;
import ch.cmbntr.modulizer.bootstrap.Operation;
import ch.cmbntr.modulizer.bootstrap.Operations.PluginLoader;
import ch.cmbntr.modulizer.bootstrap.util.ModulizerLog;
import ch.cmbntr.modulizer.bootstrap.util.Preloading;
import ch.cmbntr.modulizer.bootstrap.util.Resources.Pool;

public abstract class AbstractOperation implements Operation {

  protected void preload(final boolean requiresGUI, final String preloadSpec) {
    final ClassLoader loader = this.getClass().getClassLoader();
    Preloading.preload(requiresGUI, loader, preloadSpec);
  }

  protected static String lookupContext(final String key) {
    final BootstrapContext ctx = BootstrapContext.CURRENT.get();
    return ctx == null ? null : ctx.get(key);
  }

  protected static String putContext(final String key, final String value) {
    final BootstrapContext ctx = BootstrapContext.CURRENT.get();
    if (ctx == null) {
      throw new IllegalStateException("context was null");
    }
    return ctx.put(key, value);
  }

  protected static String lookupContextWithFallback(final String primary, final String alternate) {
    final String firstChoice = lookupContext(primary);
    return firstChoice == null ? lookupContext(alternate) : firstChoice;
  }

  protected static Future<ClassLoader> pluginLoaderViaSpecKey(final Pool pool, final String pluginSpecKey) {
    final File pluginDir = new File(lookupContext(BootstrapContext.CONFIG_KEY_APP_DIR), "bootstrap_plugins");
    return submit(pool, PluginLoader.create(pluginDir, lookupContext(pluginSpecKey)));
  }

  protected static <S extends Operation> void invokePluginOperations(final boolean restoreSystemProps,
      final Class<S> operationType, final Future<ClassLoader> pluginLoader) {
    invokeOperations(restoreSystemProps, operationType, pluginLoader);
  }

  protected static void log(final String msg, final Object... args) {
    ModulizerLog.log(msg, args);
  }

  protected static void warn(final String msg, final Object... args) {
    ModulizerLog.warn(msg, args);
  }

}
