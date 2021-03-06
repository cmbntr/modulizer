package modulizer.demo;

import java.awt.GraphicsEnvironment;
import java.io.Console;
import java.io.IOException;
import java.util.Arrays;

import javax.swing.JOptionPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class Main {

  private static final Logger LOG = LoggerFactory.getLogger(Main.class);

  static {
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();
  }

  public static void main(final String[] args) throws IOException {
    final String msg = String.format(
        "hello world\nargs: %s\napp dir:%s\nlog: %s\nbootstrap log: %s\nJavaFX WebView: %s", Arrays.toString(args),
        System.getProperty("modulizer.bootstrap.app.dir"), System.getProperty("demo.logfile"),
        System.getProperty("modulizer.logging"), findWebview());
    LOG.info(msg);

    final boolean isHeadless = GraphicsEnvironment.isHeadless();
    if (isHeadless) {
      final Console con = System.console();
      if (con != null) {
        con.writer().println(msg);
        con.readLine();
      } else {
        System.out.println(msg);
      }
    } else {
      JOptionPane.showMessageDialog(null, msg);
    }
  }

  private static Class<?> findWebview() {
    try {
      return Class.forName("javafx.scene.web.WebView", false, Main.class.getClassLoader());
    } catch (Throwable t) {
      return null;
    }
  }

}
