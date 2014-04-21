package ch.cmbntr.modulizer.bootstrap.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SocketHandler;
import java.util.logging.StreamHandler;

import ch.cmbntr.modulizer.bootstrap.BootstrapContext;

public class ModulizerLog {

  private static final Logger LOG = createLogger();

  static {
    initLogging(System.getProperty(BootstrapContext.CONFIG_KEY_LOGGING));
  }

  private ModulizerLog() {
    super();
  }

  private static Logger createLogger() {
    try {
      return Logger.getAnonymousLogger();

    } catch (RuntimeException e) {
      // workaround, for certain JDKs which can throw NPE on #getAnonymousLogger
      return Logger.getLogger("ch.cmbntr.modulizer");
    }
  }

  public static Logger getLogger() {
    return LOG;
  }

  public static void log(final String msg, final Object... args) {
    emit(Level.FINE, msg, args);
  }

  public static void warn(final String msg, final Object... args) {
    emit(Level.WARNING, msg, args);
  }

  private static void emit(final Level level, final String msg, final Object... args) {
    if (LOG.isLoggable(level)) {
      LOG.log(level, String.format(msg, args));
    }
  }

  public static void initLogging(final String config) {
    if (config != null) {
      LogConfig.configure(LOG, config);
    }
  }

  public static final class ExceptionLogger implements UncaughtExceptionHandler {

    public static ExceptionLogger create() {
      return new ExceptionLogger();
    }

    @Override
    public void uncaughtException(final Thread t, final Throwable e) {
      final StringBuilder msg = new StringBuilder(e.getMessage());
      msg.append('\n');
      final StringWriter sw = new StringWriter();
      final PrintWriter pw = new PrintWriter(sw, true);
      e.printStackTrace(pw);
      msg.append(sw.getBuffer().toString());
      LOG.severe(msg.toString());
    }
  }

  private static final class LogFormatter extends Formatter {

    public static final LogFormatter INSTANCE = new LogFormatter();

    @Override
    public String format(final LogRecord r) {
      return String.format("MODULIZER: %s,T%s,%s,%s\n", r.getMillis(), r.getThreadID(), r.getLevel(), r.getMessage());
    }
  }

  private static final class LogConfig {

    private static final Set<String> ACTIVE = new HashSet<String>();

    public static synchronized void configure(final Logger log, final String config) {
      log.setLevel(Level.ALL);
      for (final String h : config.split("\\|")) {
        if (!ACTIVE.contains(h)) {
          final StreamHandler handler = configureHandler(log, h);
          if (handler != null) {
            log.addHandler(handler);
            ACTIVE.add(h);
          }
        }
      }
    }

    private static StreamHandler createHandler(final Logger log, final String config) {
      try {
        if (config.startsWith("console")) {
          return new ConsoleHandler();
        } else if (config.startsWith("file:")) {
          return new FileHandler(config.substring(5));
        } else if (config.startsWith("file")) {
          return new FileHandler("%h/modulizer%u.log");
        } else if (config.startsWith("socket:")) {
          final URI cfg = URI.create(config);
          return new SocketHandler(cfg.getHost(), cfg.getPort());
        } else {
          return null;
        }
      } catch (final Exception e) {
        log.log(Level.CONFIG, "invalid logging config: " + config, e);
        return null;
      }
    }

    private static StreamHandler configureHandler(final Logger log, final String config) {
      final StreamHandler handler = createHandler(log, config);
      if (handler == null) {
        return null;
      }
      handler.setLevel(Level.ALL);
      handler.setFormatter(LogFormatter.INSTANCE);
      try {
        handler.setEncoding("UTF-8");
      } catch (final UnsupportedEncodingException e) {
        LOG.warning("UTF-8 not supported!");
      }
      return handler;
    }
  }

}
