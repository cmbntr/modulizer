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
    EXPORT.put(key, value);
    System.setProperty(key, value);
  }

  public static synchronized void restoreProps(final Properties origProps) {
    origProps.putAll(EXPORT);
    EXPORT.clear();
    System.setProperties(origProps);
  }

}
