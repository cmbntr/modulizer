package ch.cmbntr.modulizer.plugin.archiver;

import static ch.cmbntr.modulizer.plugin.util.ModulizerUtil.compute;
import static com.google.common.base.Functions.constant;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.Attributes;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.FileSet;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.jar.Manifest;
import org.codehaus.plexus.archiver.jar.ManifestException;
import org.codehaus.plexus.archiver.util.DefaultFileSet;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.components.io.fileselectors.FileSelector;
import org.codehaus.plexus.components.io.fileselectors.IncludeExcludeFileSelector;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;

import ch.cmbntr.modulizer.plugin.util.ModulizerUtil;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

@Component(role = ArchiverHelper.class)
public class DefaultArchiverHelper implements ArchiverHelper {

  private static final String[] MANIFEST_AND_INDEX = { "META-INF/MANIFEST.MF", "META-INF/INDEX.LIST" };

  private static final Function<File, String> FILE_NAME = new Function<File, String>() {
    @Override
    public String apply(final File f) {
      return f.getName();
    }
  };

  @Requirement
  private Logger logger;

  @Requirement(role = Archiver.class, hint = "jar")
  private JarArchiver jarArchiver;

  private void log(final String msg, final Object... args) {
    if (this.logger.isDebugEnabled()) {
      this.logger.debug(String.format(msg, args));
    }
  }

  @Override
  public void createArchive(final MavenSession session, final File target, final boolean compress,
      final Iterable<? extends ArchiverCallback> callbacks) throws MojoExecutionException {
    final MavenArchiver archiver = new MavenArchiver();
    archiver.setOutputFile(target);
    archiver.setArchiver(this.jarArchiver);

    final MavenArchiveConfiguration archive = new MavenArchiveConfiguration();
    archive.setCompress(compress);
    archive.setForced(true);

    try {
      final JarArchiver container = archiver.getArchiver();
      for (final ArchiverCallback callback : callbacks) {
        callback.addContents(container);
      }
      archiver.createArchive(session, session.getCurrentProject(), archive);
    } catch (final ArchiverException e) {
      artifactCreationFailure(e);
    } catch (final ManifestException e) {
      artifactCreationFailure(e);
    } catch (final IOException e) {
      artifactCreationFailure(e);
    } catch (final DependencyResolutionRequiredException e) {
      artifactCreationFailure(e);
    }
  }

  private void artifactCreationFailure(final Exception e) throws MojoExecutionException {
    throw new MojoExecutionException("could not create artifact", e);
  }

  @Override
  public ArchiverCallback fileAdder(final File... files) {
    return fileAdder(ImmutableList.copyOf(files));
  }

  private ArchiverCallback fileAdder(final Iterable<File> files) {
    return fileAdder(compute(files, FILE_NAME));
  }

  @Override
  public ArchiverCallback fileAdder(final Map<File, String> namedFiles) {
    return new ArchiverCallback() {
      @Override
      public void addContents(final JarArchiver archiver) {
        for (final Entry<File, String> e : namedFiles.entrySet()) {
          final File f = e.getKey();
          final String n = e.getValue();
          log("include %s as %s ", f, n);
          archiver.addFile(f, n);
        }
      }
    };
  }

  @Override
  public ArchiverCallback directoryAdder(final File... directories) {
    return directoryAdder(ImmutableList.copyOf(directories));
  }

  private ArchiverCallback directoryAdder(final Iterable<File> directories) {
    return directoryAdder(compute(directories, constant("")));
  }

  @Override
  public ArchiverCallback directoryAdder(final Map<File, String> directoriesWithPrefixes) {
    return new ArchiverCallback() {
      @Override
      public void addContents(final JarArchiver archiver) {
        for (final Entry<File, String> e : directoriesWithPrefixes.entrySet()) {
          final File d = e.getKey();
          final String p = e.getValue();
          log("include directory %s with prefix '%s'", d, p);
          archiver.addDirectory(d, p);
        }
      }
    };
  }

  @Override
  public ArchiverCallback manifest(final Map<String, String> manifestEntries) {
    return new ArchiverCallback() {
      @Override
      public void addContents(final JarArchiver archiver) {
        final Manifest m = new Manifest();
        final Attributes attrs = m.getMainAttributes();
        for (final Entry<String, String> e : manifestEntries.entrySet()) {
          attrs.putValue(e.getKey(), e.getValue());
        }
        try {
          archiver.addConfiguredManifest(m);
        } catch (final ManifestException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  @Override
  public ArchiverCallback mergeArtifacts(final File tmpDir, final Iterable<? extends Artifact> merges) {
    return new ArchiverCallback() {
      @Override
      public void addContents(final JarArchiver archiver) {
        for (final Artifact a : merges) {
          try {
            archiver.addFileSet(mergeFileSet(tmpDir, a));
          } catch (final MojoExecutionException e) {
            throw new RuntimeException(e);
          }
        }
      }
    };
  }

  private static FileSet mergeFileSet(final File tmpDir, final Artifact a) throws MojoExecutionException {
    final File destDirectory = new File(tmpDir, "extract_" + a.getId().replace(':', '_'));
    ModulizerUtil.mkdir(destDirectory);

    final ZipUnArchiver unarchiver = unzipperForMerge();
    unarchiver.setOverwrite(true);
    unarchiver.setSourceFile(a.getFile());
    unarchiver.setDestDirectory(destDirectory);
    unarchiver.extract();

    final DefaultFileSet files = new DefaultFileSet();
    files.setDirectory(destDirectory);
    return files;
  }

  private static ZipUnArchiver unzipperForMerge() {
    final ZipUnArchiver unarchiver = new ZipUnArchiver();
    unarchiver.enableLogging(new ConsoleLogger(Logger.LEVEL_ERROR, "UNZIP"));
    unarchiver.setIgnorePermissions(true);
    unarchiver.setFileSelectors(new FileSelector[] { excludeManifestAndIndex() });
    return unarchiver;
  }

  private static FileSelector excludeManifestAndIndex() {
    final IncludeExcludeFileSelector selector = new IncludeExcludeFileSelector();
    selector.setUseDefaultExcludes(false);
    selector.setExcludes(MANIFEST_AND_INDEX);
    return selector;
  }

}
