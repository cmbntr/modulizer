package ch.cmbntr.modulizer.filetree;

import static ch.cmbntr.modulizer.bootstrap.util.Resources.submit;
import static ch.cmbntr.modulizer.filetree.FileTreeUtil.isTimestampDir;
import static ch.cmbntr.modulizer.filetree.FileTreeUtil.timestamp;
import static ch.cmbntr.modulizer.filetree.FileTreeUtil.timestampDirs;
import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.errors.GitAPIException;

import ch.cmbntr.modulizer.bootstrap.BootstrapContext;
import ch.cmbntr.modulizer.bootstrap.impl.AbstractPrepare;
import ch.cmbntr.modulizer.bootstrap.util.ModulizerIO;
import ch.cmbntr.modulizer.filetree.Restore.CleanupMode;

public class FileTreePrepare extends AbstractPrepare {

  private static final Pattern COPY_SRC_PATTERN = Pattern.compile("modulizer\\.filetree\\.copy\\.([^.]*)\\.src");

  private static final String COPY_FALLBACK_TEMPLATE = "modulizer.filetree.copy.%s.fallback";

  private static final String COPY_DEST_TEMPLATE = "modulizer.filetree.copy.%s.dest";

  private static final long EXTRA_COPY_HASHING_TIMEOUT_MINUTES = 5L;

  private static final int NUM_ATTEMPTS = 3;

  @Override
  public void run() {
    final File baseDir = determineWorkDirBase();
    final boolean ignoreExisting = determineIgnoreExisting();
    final CleanupMode cleanup = determineCleanupMode();
    int attempt = 1;
    while (attempt <= NUM_ATTEMPTS) {
      try {
        final Future<URI> bundle = findBundle();

        final File moduleRepo = determineDestination(baseDir, ignoreExisting || attempt != 1);
        final String requiredRef = determineRequiredRef();
        final String bundleRef = determineBundleRef();

        log("prepare file tree at %s", moduleRepo);
        Restore.restore(moduleRepo, "master", requiredRef, bundle, bundleRef, cleanup);

        performExtraCopyJobs(findExtraCopyJobs());
        return;

      } catch (final IOException e) {
        failPrepare(e, attempt);
      } catch (final GitAPIException e) {
        failPrepare(e, attempt);
      } catch (final RuntimeException e) {
        failPrepare(e, attempt);
      } finally {
        attempt++;
      }
    }
  }

  private void failPrepare(final Throwable e, final int attempt) {
    warn("restoring file tree failed: %s", e);
    if (attempt >= NUM_ATTEMPTS) {
      throw new RuntimeException(e);
    }
  }

  private boolean determineIgnoreExisting() {
    return Boolean.parseBoolean(lookupContext(BootstrapContext.CONFIG_KEY_IGNORE_EXISTING, "false"));
  }

  private CleanupMode determineCleanupMode() {
    return CleanupMode.valueOf(lookupContext(BootstrapContext.CONFIG_KEY_FILETREE_CLEANUP, "FULL"));
  }

  private File determineWorkDirBase() {
    return new File(lookupContext(BootstrapContext.CONFIG_KEY_APP_DIR));
  }

  private File determineDestination(final File baseDir, final boolean ignoreExisting) throws IOException {
    final File existing = ignoreExisting ? null : findExistingDestination(baseDir);
    final File repo = existing == null ? new File(baseDir, timestamp()) : existing;
    putContext(BootstrapContext.CONFIG_KEY_APP_DIR, repo.getAbsolutePath());
    return repo;
  }

  private File findExistingDestination(final File baseDir) {
    if (!baseDir.isDirectory()) {
      return null;
    }
    final String[] contents = baseDir.list(timestampDirs());
    if (contents == null || contents.length == 0) {
      return null;
    }
    Arrays.sort(contents);
    final File existing = new File(baseDir, contents[contents.length - 1]);
    if (existing.isDirectory() && isTimestampDir(existing)) {
      return existing;
    }
    return null;
  }

  private String determineRequiredRef() {
    return lookupContext(BootstrapContext.CONFIG_KEY_BUNDLE_ID);
  }

  private Future<URI> findBundle() {
    return submit(new Callable<URI>() {
      @Override
      public URI call() throws Exception {
        final String given = lookupContext(BootstrapContext.CONFIG_KEY_BUNDLE_URI,
            BootstrapContext.DEFAULT_BUNDLE_URI);
        final URI bundleURI = URI.create(given);
        if (bundleURI.isAbsolute()) {
          return bundleURI;
        }

        final URL bundle = FileTreePrepare.class.getResource(given);
        if (bundle == null) {
          throw new RuntimeException("could not find bundle resource: " + given);
        }
        return bundle.toURI();
      }
    });
  }

  private String determineBundleRef() {
    return lookupContext(BootstrapContext.CONFIG_KEY_BUNDLE_REF, BootstrapContext.DEFAULT_BUNDLE_REF);
  }

  private String lookupContext(final String key, final String defaultValue) {
    final String value = lookupContext(key);
    return value == null ? defaultValue : value;
  }

  private Map<File, Map<String, URL>> findExtraCopyJobs() {
    final Map<File, Map<String, URL>> jobsByDest = new LinkedHashMap<File, Map<String, URL>>();
    for (final String key : findMatchingContextKeys(COPY_SRC_PATTERN)) {
      final Matcher m = COPY_SRC_PATTERN.matcher(key);
      if (m.matches()) {
        final String jobLabel = m.group(1);
        final String src = lookupContextInterpolated(key);
        final String srcFallback = lookupContextInterpolated(format(COPY_FALLBACK_TEMPLATE, jobLabel));
        final String dest = lookupContextInterpolated(format(COPY_DEST_TEMPLATE, jobLabel));
        if (dest == null) {
          warn("missing dest for copy job '%s'", jobLabel);

        } else {
          try {
            final Map<String, URL> job = Collections.singletonMap(jobLabel, copySourceToURL(src, srcFallback));
            jobsByDest.put(copyDestinationToFile(dest), job);
          } catch (final RuntimeException e) {
            failExtraCopyJob(jobLabel, e);
          }
        }
      }
    }
    return jobsByDest;
  }

  private static URL copySourceToURL(final String src, String srcFallback) {
    try {
      final File f = tryFindReadableFile(src, srcFallback);
      if (f != null) {
        return f.toURI().toURL();
      } else {
        log("copy job source is not a readable file, try as URL");
        return new URL(src);
      }

    } catch (final MalformedURLException e) {
      throw new RuntimeException("could not create source URL for copy job, src=" + src, e);
    }
  }

  private static File tryFindReadableFile(String... paths) {
    for (String path : paths) {
      final File f = new File(path).getAbsoluteFile();
      if (f.canRead()) {
        return f;
      }
    }
    return null;
  }

  private File copyDestinationToFile(final String dest) {
    final File f = new File(dest).getAbsoluteFile();
    final File p = f.getParentFile();
    if (p.isDirectory()) {
      return f;
    } else {
      throw new RuntimeException("parent directory for copy job does not exist, dest=" + dest);
    }
  }

  private void performExtraCopyJobs(final Map<File, Map<String, URL>> jobsByDest) {
    for (final Entry<File, Map<String, URL>> destAndSrcByLabel : jobsByDest.entrySet()) {
      for (final Entry<String, URL> entry : destAndSrcByLabel.getValue().entrySet()) {
        final String jobLabel = entry.getKey();
        try {
          final URL src = entry.getValue();
          final File dest = destAndSrcByLabel.getKey();

          log("copy job '%s': [%s] to [%s]", jobLabel, src, dest);
          if (canSkipCopy(src, dest)) {
            log("skip job '%s', because file exists and content matches", jobLabel);
          } else {
            ModulizerIO.copyStream(src, dest);
          }

        } catch (final IOException e) {
          failExtraCopyJob(jobLabel, e);
        } catch (final RuntimeException e) {
          failExtraCopyJob(jobLabel, e);
        }
      }
    }
  }

  private boolean canSkipCopy(final URL src, final File dest) {
    try {
      if (dest.exists()) {
        final URI s = src.toURI();
        final URI d = dest.toURI();
        final Map<URI, Future<String>> x = ModulizerIO.sha1URIasync(Arrays.asList(s, d));
        final String srcHash = hashOrNull(x.get(s));
        final String destHash = hashOrNull(x.get(d));
        return srcHash != null && destHash != null && srcHash.equals(destHash);
      }
      return false;

    } catch (final URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  private String hashOrNull(final Future<String> hash) {
    try {
      return hash.get(EXTRA_COPY_HASHING_TIMEOUT_MINUTES, TimeUnit.MINUTES);

    } catch (final ExecutionException e) {
      log("hashing failed: %s", e.getCause().getMessage());
    } catch (final InterruptedException e) {
      log("hashing interrupted");
    } catch (final TimeoutException e) {
      log("hashing timout");
    }
    return null;
  }

  private static void failExtraCopyJob(final String jobLabel, final Exception e) {
    warn("copy job '%s' failed: %s", jobLabel, e.getMessage());
    throw new RuntimeException(format("copy job '%s' failed", jobLabel), e);
  }

}
