package ch.cmbntr.modulizer.filetree;

import java.io.File;
import java.util.GregorianCalendar;

import javax.xml.bind.DatatypeConverter;

public class FileTreeUtil {

  private FileTreeUtil() {
    super();
  }

  public static String timestamp() {
    return DatatypeConverter.printDateTime(new GregorianCalendar()).replace(':', '_');
  }

  public static boolean isTimestampDir(final File existing) {
    try {
      final String name = existing.getName();
      if (name.length() == 0 || name.charAt(0) == '.') {
        return false;
      }
      DatatypeConverter.parseDateTime(name.replace('_', ':'));
      return true;
    } catch (final IllegalArgumentException e) {
      return false;
    }
  }

}
