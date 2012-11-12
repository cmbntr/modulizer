package ch.cmbntr.modulizer.filetree;

import java.io.File;
import java.io.FilenameFilter;
import java.util.GregorianCalendar;
import java.util.regex.Pattern;

import javax.xml.bind.DatatypeConverter;

public class FileTreeUtil {

  private static final Pattern DIR_PREFIX = Pattern.compile("\\d\\d\\d\\d-\\d\\d-\\d\\dT");

  private static final FilenameFilter DIR_FILTER = new FilenameFilter() {
    @Override
    public boolean accept(File dir, String name) {
      return DIR_PREFIX.matcher(name).lookingAt();
    }
  };

  private FileTreeUtil() {
    super();
  }

  public static String timestamp() {
    return DatatypeConverter.printDateTime(new GregorianCalendar()).replace(':', '_');
  }

  public static FilenameFilter timestampDirs() {
    return DIR_FILTER;
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
