package ch.cmbntr.modulizer.filetree;

import static ch.cmbntr.modulizer.bootstrap.util.Resources.submit;
import static ch.cmbntr.modulizer.filetree.FileTreeUtil.isTimestampDir;
import static ch.cmbntr.modulizer.filetree.FileTreeUtil.timestamp;
import static ch.cmbntr.modulizer.filetree.FileTreeUtil.timestampDirs;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.eclipse.jgit.api.errors.GitAPIException;

import ch.cmbntr.modulizer.bootstrap.BootstrapContext;
import ch.cmbntr.modulizer.bootstrap.impl.AbstractPrepare;
import ch.cmbntr.modulizer.filetree.Restore.CleanupMode;

public class FileTreePrepare extends AbstractPrepare {

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
            BootstrapContext.DEFAULT_KEY_BUNDLE_URI);
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
    return lookupContext(BootstrapContext.CONFIG_KEY_BUNDLE_REF, BootstrapContext.DEFAULT_KEY_BUNDLE_REF);
  }

  private String lookupContext(final String key, final String defaultValue) {
    final String value = lookupContext(key);
    return value == null ? defaultValue : value;
  }
}
