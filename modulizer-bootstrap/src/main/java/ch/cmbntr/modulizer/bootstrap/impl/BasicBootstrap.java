package ch.cmbntr.modulizer.bootstrap.impl;

import static ch.cmbntr.modulizer.bootstrap.util.ModulizerIO.closeQuietly;
import static ch.cmbntr.modulizer.bootstrap.util.ModulizerIO.mkdir;
import static ch.cmbntr.modulizer.bootstrap.util.ModulizerLog.initLogging;
import static java.lang.Boolean.parseBoolean;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.URLDecoder;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Future;

import ch.cmbntr.modulizer.bootstrap.Bootstrap;
import ch.cmbntr.modulizer.bootstrap.BootstrapContext;
import ch.cmbntr.modulizer.bootstrap.Launch;
import ch.cmbntr.modulizer.bootstrap.Prepare;
import ch.cmbntr.modulizer.bootstrap.util.Resources;
import ch.cmbntr.modulizer.bootstrap.util.Resources.Pool;
import ch.cmbntr.modulizer.bootstrap.util.SystemPropertyHelper;

public class BasicBootstrap extends AbstractOperation implements Bootstrap {

  /**
   * {@inheritDoc}
   */
  @Override
  public void run() {
    final Pool handle = Resources.getPoolHandle();
    try {
      establishInitialContext();
      performSecuritySettings();
      handleSystemProperties();
      sanitizeContext();
      initializeLogging();
      exportProperties();
      verboseLoading();
      preloading();

      final Future<ClassLoader> prepareLoader = preparePluginLoader(handle);
      final Future<ClassLoader> launchLoader = launchPluginLoader(handle);

      prepare(prepareLoader);
      launch(launchLoader);
      scheduleGC();
    } finally {
      clearContext();
      Resources.dispose(handle);
    }
  }

  private void establishInitialContext() {
    final InputStream config = BasicBootstrap.class.getResourceAsStream(BootstrapContext.CONFIG_NAME);
    if (config == null) {
      warn("config not found: %s", BootstrapContext.CONFIG_NAME);
    }
    try {
      final PropertiesContext ctx = PropertiesContext.empty().loadFromXML(config);
      ctx.put(BootstrapContext.CONFIG_KEY_UUID, UUID.randomUUID().toString());
      BootstrapContext.CURRENT.set(ctx);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    } finally {
      closeQuietly(config);
    }
  }

  private void performSecuritySettings() {
    try {
      if (!parseBoolean(lookupContext(BootstrapContext.CONFIG_KEY_SECURITY_SKIP))) {
        log("applying security settings");
        System.setSecurityManager(null);
      }
    } catch (final SecurityException e) {
      warn("failed to apply security settings: %s", e);
    }
  }

  private void handleSystemProperties() {
    setSystemPropertiesFromArgs();
    final BootstrapContext ctx = BootstrapContext.CURRENT.get();
    if (ctx instanceof PropertiesContext) {
      ((PropertiesContext) ctx).addSystemProperties();
    }
  }

  private void sanitizeContext() {
    final BootstrapContext ctx = BootstrapContext.CURRENT.get();
    ctx.put(BootstrapContext.CONFIG_KEY_APP_ID, sanitizeAppId(ctx));
    try {
      ctx.put(BootstrapContext.CONFIG_KEY_APP_DIR, sanitizeAppDir(ctx));
    } catch (final IOException e) {
      warn("failed to sanitize %s: %s", BootstrapContext.CONFIG_KEY_APP_DIR, e);
    }
  }

  private void initializeLogging() {
    String loggingConfig = lookupContext(BootstrapContext.CONFIG_KEY_LOGGING);
    if ("app.dir".equals(loggingConfig)) {
      loggingConfig = "file:" + lookupContext(BootstrapContext.CONFIG_KEY_APP_DIR) + "/bootstrap.log";
      putContext(BootstrapContext.CONFIG_KEY_LOGGING, loggingConfig);
    }
    initLogging(loggingConfig);
  }

  private void exportProperties() {
    final BootstrapContext ctx = BootstrapContext.CURRENT.get();
    export(ctx, BootstrapContext.CONFIG_KEY_UUID);
    export(ctx, BootstrapContext.CONFIG_KEY_APP_ID);
    export(ctx, BootstrapContext.CONFIG_KEY_APP_DIR);
    export(ctx, BootstrapContext.CONFIG_KEY_LOGGING);
  }

  private void export(final BootstrapContext ctx, final String key) {
    final String val = ctx.get(key);
    if (val != null) {
      SystemPropertyHelper.export(key, val);
    }
  }

  private void verboseLoading() {
    final String val = lookupContext(BootstrapContext.CONFIG_KEY_VERBOSE_LOADING_MILLIS);
    final long verboseLoadingMillis = val == null ? -1L : Long.parseLong(val);
    verboseClassloading(verboseLoadingMillis);
  }

  private static void verboseClassloading(final long durationMillis) {
    if (durationMillis > 0L) {
      ManagementFactory.getClassLoadingMXBean().setVerbose(true);
      Resources.delay(durationMillis, new Runnable() {
        @Override
        public void run() {
          ManagementFactory.getClassLoadingMXBean().setVerbose(false);
        }
      });
    }
  }

  private void preloading() {
    preload(false, lookupContext(BootstrapContext.CONFIG_KEY_PRELOAD));
    preload(true, lookupContext(BootstrapContext.CONFIG_KEY_PRELOAD_GUI));
  }

  private String sanitizeAppId(final BootstrapContext ctx) {
    final String given = ctx.get(BootstrapContext.CONFIG_KEY_APP_ID);
    return given == null ? "unnamed-" + ctx.get(BootstrapContext.CONFIG_KEY_UUID) : given.trim();
  }

  private String sanitizeAppDir(final BootstrapContext ctx) throws IOException {
    final String given = ctx.get(BootstrapContext.CONFIG_KEY_APP_DIR);
    return ensureAppDir(given == null ? determineDefaultAppDir(ctx) : new File(given));
  }

  private File determineBootstrapBaseDir(final BootstrapContext ctx) {
    final String baseDir = ctx.get(BootstrapContext.CONFIG_KEY_BASE_DIR);
    return new File(baseDir == null ? System.getProperty("java.io.tmpdir") : baseDir);
  }

  private File determineDefaultAppDir(final BootstrapContext ctx) {
    final String appId = ctx.get(BootstrapContext.CONFIG_KEY_APP_ID);
    final File appDir = new File(determineBootstrapBaseDir(ctx), appId);
    final String slot = ctx.get(BootstrapContext.CONFIG_KEY_APP_DIR_SLOT);
    return slot == null ? appDir : new File(appDir, slot.trim());
  }

  private String ensureAppDir(final File appDir) throws IOException {
    mkdir(appDir);
    return appDir.getCanonicalFile().getAbsolutePath();
  }

  private Future<ClassLoader> preparePluginLoader(final Pool handle) {
    return pluginLoaderViaSpecKey(handle, BootstrapContext.CONFIG_KEY_PREPARE_PLUGINS);
  }

  private void prepare(final Future<ClassLoader> loader) {
    invokePluginOperations(true, Prepare.class, loader);
  }

  private Future<ClassLoader> launchPluginLoader(final Pool handle) {
    return pluginLoaderViaSpecKey(handle, BootstrapContext.CONFIG_KEY_LAUNCH_PLUGINS);
  }

  private void launch(final Future<ClassLoader> loader) {
    invokePluginOperations(false, Launch.class, loader);
  }

  private void scheduleGC() {
    final String val = lookupContext(BootstrapContext.CONFIG_KEY_GC_DELAY);
    final long delay = val == null ? BootstrapContext.DEFAULT_GC_DELAY_MS : Long.parseLong(val);
    delayedGC(delay);
  }

  private static void delayedGC(final long delay) {
    if (delay >= 0L) {
      Resources.delay(delay, new Runnable() {
        @Override
        public void run() {
          ManagementFactory.getMemoryMXBean().gc();
        }
      });
    }
  }

  private void clearContext() {
    final BootstrapContext ctxt = BootstrapContext.CURRENT.getAndSet(null);
    log("final context: %s", ctxt);
  }

  private static void setSystemPropertiesFromArgs() {
    final String[] args = BootstrapContext.ARGS.get();
    if (args != null && args.length > 1 && "--systemProperties".equals(args[0])) {
      final Properties props = decodeProperties(args[1]);
      for (final Entry<Object, Object> p : props.entrySet()) {
        final String k = p.getKey().toString();
        final String v = p.getValue().toString();
        System.setProperty(k, v);
        log("set system property %s to %s", k, v);
      }
    }
  }

  private static Properties decodeProperties(final String encoded) {
    final Properties p = new Properties();
    try {
      final String dec = URLDecoder.decode(encoded.trim(), "UTF-8");
      final InputStream is = new ByteArrayInputStream(dec.getBytes("UTF-8"));
      try {
        p.loadFromXML(is);
      } finally {
        is.close();
      }
    } catch (final IOException e) {
      warn("properties decoding failed: %s", e);
    }
    return p;
  }

}
