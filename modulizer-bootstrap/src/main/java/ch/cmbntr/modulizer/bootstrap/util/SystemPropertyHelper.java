package ch.cmbntr.modulizer.bootstrap.util;


import java.util.Properties;



public class SystemPropertyHelper {

  private SystemPropertyHelper() {
    super();
  }

  public static Properties snapshotProps() {
    final Properties snapshot = new Properties();
    snapshot.putAll(System.getProperties());
    return snapshot;
  }

  public static void restoreProps(final Properties origProps) {
    System.setProperties(origProps);
  }

}
