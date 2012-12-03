package ch.cmbntr.modulizer.bootstrap.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class SystemPropertyHelper {

  private static final Map<String, String> EXPORT = new HashMap<String, String>();

  private SystemPropertyHelper() {
    super();
  }

  public static Properties snapshotProps() {
    final Properties snapshot = new Properties();
    snapshot.putAll(System.getProperties());
    return snapshot;
  }

  public static synchronized void export(final String key, final String value) {
    try {
      EXPORT.put(key, value);
      System.setProperty(key, value);
    } catch (final SecurityException e) {
      ModulizerLog.warn("could not set property %s to %s", key, value);
    }
  }

  public static synchronized void restoreProps(final Properties origProps) {
    if (origProps == null) {
      return;
    }

    try {
      origProps.putAll(EXPORT);
      EXPORT.clear();
      System.setProperties(origProps);
    } catch (final SecurityException e) {
      ModulizerLog.warn("could not restore properties");
    }
  }

}
