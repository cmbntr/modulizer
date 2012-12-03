package ch.cmbntr.modulizer.bootstrap;

import static ch.cmbntr.modulizer.bootstrap.util.ModulizerIO.copyStream;
import static ch.cmbntr.modulizer.bootstrap.util.ModulizerIO.mkdir;
import static ch.cmbntr.modulizer.bootstrap.util.ModulizerIO.verifySHA1Named;
import static ch.cmbntr.modulizer.bootstrap.util.ModulizerLog.log;
import static ch.cmbntr.modulizer.bootstrap.util.ModulizerLog.warn;
import static ch.cmbntr.modulizer.bootstrap.util.SystemPropertyHelper.restoreProps;
import static ch.cmbntr.modulizer.bootstrap.util.SystemPropertyHelper.snapshotProps;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import ch.cmbntr.modulizer.bootstrap.util.Resources;

public class Operations {

  private Operations() {
    super();
  }

  public static ClassLoader defaultLoader() {
    final ClassLoader tcl = Thread.currentThread().getContextClassLoader();
    return tcl == null ? Operations.class.getClassLoader() : tcl;
  }

  public static <S extends Operation> void invokeOperations(final boolean restoreSystemProps,
      final Class<S> operationType, final Future<ClassLoader> loader) {
    invokeOperations(restoreSystemProps, operationType, Resources.get(loader, "failed to get classloader"));
  }

  public static <S extends Operation> void invokeOperations(final boolean restoreSystemProps,
      final Class<S> operationType, final ClassLoader loader) {
    log("invoke %s operations", operationType.getSimpleName());
    final Thread currentThread = Thread.currentThread();
    final ClassLoader origCCL = currentThread.getContextClassLoader();
    final Properties origProps = restoreSystemProps ? snapshotProps() : null;
    try {
      final Iterator<S> ops = findOperations(operationType, loader);
      while (ops.hasNext()) {
        final Operation op = ops.next();
        final Class<? extends Operation> operationClass = op.getClass();
        final ClassLoader opLoader = operationClass.getClassLoader();
        log("invoke %s from %s", operationClass.getName(), classLoaderInfo(opLoader));
        currentThread.setContextClassLoader(opLoader);
        op.run();
      }
    } finally {
      currentThread.setContextClassLoader(origCCL);
      restoreProps(origProps);
    }
  }

  private static <S extends Operation> Iterator<S> findOperations(final Class<S> operationType, final ClassLoader loader) {
    return ServiceLoader.load(operationType, loader).iterator();
  }

  private static String classLoaderInfo(final ClassLoader cl) {
    if (cl instanceof URLClassLoader) {
      final URLClassLoader ucl = (URLClassLoader) cl;
      return Arrays.toString(ucl.getURLs());
    } else {
      return cl.toString();
    }
  }

  public static final class PluginLoader implements Callable<ClassLoader> {

    private final File dir;
    private final String spec;

    private PluginLoader(final File dir, final String spec) {
      this.dir = dir;
      this.spec = spec;
    }

    public static PluginLoader create(final File dir, final String spec) {
      return new PluginLoader(dir, spec);
    }

    @Override
    public ClassLoader call() throws Exception {
      return pluginLoader(defaultLoader(), this.dir, this.spec);
    }

    private static ClassLoader pluginLoader(final ClassLoader parent, final File pluginDir, final String pluginSpec) {
      if (pluginSpec == null) {
        log("no plugins specified");
        return parent;
      }
      try {
        final List<URL> plugins = new LinkedList<URL>();
        for (final String url : pluginSpec.split(",")) {
          final String pluginURI = url.trim();
          log("plugin: %s", pluginURI);
          final URI p = URI.create(pluginURI);
          if (p.isAbsolute()) {
            plugins.add(p.toURL());

          } else {
            mkdir(pluginDir);
            final String jarPath = p.getPath();
            final File pluginDest = new File(pluginDir, jarPath);
            if (verifySHA1Named(pluginDest)) {
              plugins.add(pluginDest.toURI().toURL());
            } else {
              final URL jar = parent.getResource(jarPath);
              if (jar == null) {
                warn("could not find plugin jar:  %s", jarPath);
              } else {
                plugins.add(copyStream(jar, pluginDest));
              }
            }
          }
        }

        log("plugins: %s", plugins);

        if (plugins.isEmpty()) {
          return parent;
        } else {
          return new URLClassLoader(toURLArray(plugins), parent);
        }
      } catch (final MalformedURLException e) {
        throw new RuntimeException(e);
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }

    private static URL[] toURLArray(final List<URL> urls) {
      final URL[] a = new URL[urls.size()];
      return urls.toArray(a);
    }

  }

}
