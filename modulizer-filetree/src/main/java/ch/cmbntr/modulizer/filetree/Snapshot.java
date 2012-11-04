package ch.cmbntr.modulizer.filetree;

import java.io.Console;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Logger;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.UnmergedPathsException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.BundleWriter;

public class Snapshot {

  private static final Logger LOG = Logger.getAnonymousLogger();

  private Snapshot() {
    super();
  }

  private static void log(final String msg, final Object... args) {
    LOG.fine(String.format(msg, args));
  }

  public static String sanitizeHeadRef(final String bundleRef) {
    final String headsNamespace = "refs/heads/";
    final String ref = bundleRef.replace('\\', '/');
    return ref.contains(headsNamespace) ? ref : "refs/heads/" + bundleRef;
  }

  public static void main(final String[] args) throws Exception {
    final File here = new File(".").getCanonicalFile().getAbsoluteFile();
    final File bundle = new File(FileTreeUtil.timestamp() + ".bundle");
    System.out.format("Created snapshot %s of %s to %s", createBundle(bundle, here, "master"), here, bundle);
    System.exit(0);
  }

  public static String createBundle(final File bundle, final File worktree, final String bundleRef) {

    try {
      final File dir = worktree.getCanonicalFile().getAbsoluteFile();

      final Git git = Git.init().setDirectory(dir).call();

      final AddCommand add = git.add();
      for (final File f : dir.listFiles()) {
        final String name = f.getName();
        if (!".git".equals(name)) {
          log("adding %s", name);
          add.addFilepattern(name);
        }
      }
      final DirCache index = add.call();
      final int cnt = index.getEntryCount();
      log("%d entries added", cnt);
      final String msg = "snapshot";
      final RevCommit head = git.commit().setMessage(msg).call();

      final BundleWriter bw = new BundleWriter(git.getRepository());
      bw.include(sanitizeHeadRef(bundleRef), head);

      final FileOutputStream out = new FileOutputStream(bundle);
      try {
        bw.writeBundle(createProgressMonitor(), out);
      } finally {
        out.close();
      }

      return head.getId().getName();

    } catch (final NoFilepatternException e) {
      throw failCreateBundle(e);
    } catch (final NoHeadException e) {
      throw failCreateBundle(e);
    } catch (final NoMessageException e) {
      throw failCreateBundle(e);
    } catch (final UnmergedPathsException e) {
      throw failCreateBundle(e);
    } catch (final ConcurrentRefUpdateException e) {
      throw failCreateBundle(e);
    } catch (final WrongRepositoryStateException e) {
      throw failCreateBundle(e);
    } catch (final IOException e) {
      throw failCreateBundle(e);
    } catch (final GitAPIException e) {
      throw failCreateBundle(e);
    }
  }

  private static ProgressMonitor createProgressMonitor() {
    final Console con = System.console();
    return con == null ? NullProgressMonitor.INSTANCE : new TextProgressMonitor(con.writer());
  }

  private static RuntimeException failCreateBundle(final Throwable cause) {
    throw new FileTreeSnapshotException(cause);
  }

  public static class FileTreeSnapshotException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FileTreeSnapshotException(final Throwable cause) {
      super(cause);
    }

  }

}
