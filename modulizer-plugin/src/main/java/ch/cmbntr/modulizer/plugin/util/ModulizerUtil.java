package ch.cmbntr.modulizer.plugin.util;

import static com.google.common.collect.Iterables.transform;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.FileUtils;

import ch.cmbntr.modulizer.bootstrap.util.ModulizerIO;
import ch.cmbntr.modulizer.bootstrap.util.Resources;
import ch.cmbntr.modulizer.bootstrap.util.Resources.Pool;

import com.google.common.base.Function;
import com.google.common.collect.ComputationException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

public class ModulizerUtil {

  @SuppressWarnings("rawtypes")
  private static final Function FUTURE_COLLECTOR = new Function<Future<?>, Object>() {
    @Override
    public Object apply(final Future<?> t) {
      try {
        return t.get();
      } catch (final InterruptedException e) {
        Thread.interrupted();
        throw new ComputationException(e);
      } catch (final ExecutionException e) {
        throw new ComputationException(e.getCause());
      }
    }
  };

  private ModulizerUtil() {
    super();
  }

  @SuppressWarnings("unchecked")
  public static <T> Function<Future<T>, T> futureCollector() {
    return FUTURE_COLLECTOR;
  }

  public static <T> List<T> collect(final Iterable<Future<T>> values) {
    return ImmutableList.copyOf(transform(values, ModulizerUtil.<T> futureCollector()));
  }

  public static <F, T> Map<F, T> compute(final Iterable<F> items, final Function<? super F, T> f) {
    final ImmutableMap.Builder<F, T> result = ImmutableMap.builder();
    for (final F i : items) {
      result.put(i, f.apply(i));
    }
    return result.build();
  }

  public static <F, T> Map<F, T> computeLazyAsync(final Iterable<F> items, final Function<? super F, T> f)
      throws ComputationException {
    return Maps.transformValues(submitCompute(items, f), ModulizerUtil.<T> futureCollector());
  }

  private static <F, T> Map<F, Future<T>> submitCompute(final Iterable<F> items, final Function<? super F, T> f) {
    final Pool pool = Resources.getPoolHandle();
    final ScheduledExecutorService exec = pool.aquireBlockableExec();
    try {
      final ImmutableMap.Builder<F, Future<T>> submits = ImmutableMap.builder();
      for (final F i : items) {
        submits.put(i, exec.submit(new Callable<T>() {
          @Override
          public T call() throws Exception {
            return f.apply(i);
          }
        }));
      }
      return submits.build();
    } finally {
      pool.releaseBlockableExec(exec);
    }
  }

  public static void mkdir(final File dir) throws MojoExecutionException {
    final String path = dir.getPath();
    FileUtils.mkdir(path);
    if (!dir.isDirectory()) {
      throw new MojoExecutionException("could not create " + path);
    }
  }

  public static String sha1Name(final File f) throws MojoExecutionException {
    try {
      return ModulizerIO.sha1Name(f);
    } catch (final IOException e) {
      throw new MojoExecutionException("could not sha1 hash the file " + f);
    }
  }

}
