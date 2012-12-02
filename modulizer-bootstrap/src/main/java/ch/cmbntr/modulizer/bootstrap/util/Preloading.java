package ch.cmbntr.modulizer.bootstrap.util;

import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Preloading {

  private Preloading() {
    super();
  }

  public static void preload(final boolean requiresGUI, final ClassLoader loader, final String preloadSpec) {
    if (preloadSpec == null) {
      return;
    }
    Resources.execute(new Runnable() {
      @Override
      public void run() {
        if (requiresGUI && GraphicsEnvironment.isHeadless()) {
          return;
        }
        for (final List<String> slice : parsePreloadSpec(preloadSpec)) {
          Resources.delay(0L, new Runnable() {
            @Override
            public void run() {
              for (final String clazz : slice) {
                try {
                  Class.forName(clazz, true, loader);
                } catch (final ClassNotFoundException e) {
                  ModulizerLog.warn("preload of %s failed: %s", clazz, e.getMessage());
                }
              }
            }
          });
        }
      }
    });
  }

  private static List<List<String>> parsePreloadSpec(final String barSeparatedClassLists) {
    if (barSeparatedClassLists == null) {
      return Collections.emptyList();
    }

    final String[] slices = barSeparatedClassLists.split("\\|");
    if (slices.length == 1) {
      return Collections.singletonList(classNames(slices[0]));
    } else {
      final List<List<String>> result = new ArrayList<List<String>>(slices.length);
      for (final String slice : slices) {
        final List<String> classes = classNames(slice);
        if (!classes.isEmpty()) {
          result.add(classes);
        }
      }
      return result;
    }
  }

  public static List<String> classNames(final String commaSeparatedClasses) {
    final String[] classes = commaSeparatedClasses.split(",");
    final List<String> result = new ArrayList<String>(classes.length);
    for (final String clazz : classes) {
      final String trimmed = clazz.trim();
      if (trimmed.length() > 0) {
        result.add(trimmed);
      }
    }
    return result;
  }

}
