package modulizer.demo;

import java.awt.GraphicsEnvironment;
import java.io.Console;
import java.io.IOException;
import java.util.Arrays;

import javax.swing.JOptionPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

  private static final Logger LOG = LoggerFactory.getLogger(Main.class);

  public static void main(final String[] args) throws IOException {
    final String msg = "hello world\n" + Arrays.toString(args);
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

}
