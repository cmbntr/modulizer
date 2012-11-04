package ch.cmbntr.modulizer.filetree;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportBundleStream;
import org.eclipse.jgit.transport.URIish;

import ch.cmbntr.modulizer.bootstrap.util.Resources;

public class Restore {

  private static final Logger LOG = Logger.getAnonymousLogger();

  private Restore() {
    super();
  }

  private static void log(final String msg, final Object... args) {
    LOG.fine(String.format(msg, args));
  }

  public static void restore(final File destination, final String branch, final String requiredRef,
      final Future<URI> bundle, final String bundleRef, final CleanupMode cleanup) throws IOException, GitAPIException {
    try {
      final Git git = exists(destination);
      try {
        if (git == null || !headAt(git, requiredRef)) {
          final URI b = Resources.get(bundle, "failed to get bundle");
          explodeBundle(destination, branch, bundleRef, b, cleanup);
        } else {
          bundle.cancel(true);
          ensureClean(git, cleanup);
        }
      } finally {
        if (git != null) {
          git.getRepository().close();
        }
      }
    } finally {
      RepositoryCache.clear();
    }
    log("prepared");
  }

  private static void ensureClean(final Git git, final CleanupMode cleanup) throws NoWorkTreeException, GitAPIException {

    if (CleanupMode.NONE.equals(cleanup)) {
      log("skip cleanup");
      return;
    }
    //TODO handle all cleanup modes properly
    final Status st = git.status().call();
    if (st.isClean()) {
      log("ready");
    } else {
      log("not clean");
      git.reset().setMode(ResetType.HARD).call();
      final Status st2 = git.status().call();
      if (st2.isClean() || !st.getUntracked().isEmpty()) {
        log("ready");
      }
    }
  }

  private static Git exists(final File worktree) {
    try {
      return Git.open(worktree);

    } catch (final IOException e) {
      return null;
    }
  }

  private static boolean headAt(final Git git, final String required) {
    try {
      final Repository repo = git.getRepository();
      final String branch = repo.getFullBranch();

      final Ref ref = repo.getRef(branch);
      return ref != null && required.equals(ref.getObjectId().getName());

    } catch (final IOException e) {
      return false;
    }
  }

  private static String explodeBundle(final File worktree, final String branchName, final String refName,
      final URI bundle, final CleanupMode cleanup) throws IOException, GitAPIException {

    if (!worktree.exists() && !worktree.mkdirs()) {
      throw new IOException("could not create " + worktree);
    }
    if (!worktree.canWrite()) {
      throw new IOException("not writable: " + worktree);
    }

    final Git git = Git.init().setDirectory(worktree).call();
    try {

      // fetch
      final String ref = fetchBundle(git, bundle, refName);

      // branch
      git.branchCreate().setName(branchName).setForce(true).setStartPoint(ref).call();

      // checkout
      final String head = git.checkout().setName(branchName).call().getObjectId().getName();

      // clean workspace
      ensureClean(git, cleanup);

      return head;

    } finally {
      // close repository
      git.getRepository().close();
    }

  }

  private static String fetchBundle(final Git git, final URI bundle, final String refName) throws IOException {
    final URL loc = bundle.toURL();
    final URIish uri = new URIish(loc);

    final Transport t = new TransportBundleStream(git.getRepository(), uri, loc.openStream());
    try {
      final FetchConnection src = t.openFetch();
      final Ref target = src.getRef(refName);

      final Collection<Ref> want = singleton(target);
      final Set<ObjectId> have = emptySet();
      src.fetch(NullProgressMonitor.INSTANCE, want, have);

      return target.getObjectId().getName();

    } finally {
      t.close();
    }
  }

  public enum CleanupMode {
    NONE, RESET_ONLY, FULL
  }

}
