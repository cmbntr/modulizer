package ch.cmbntr.modulizer.plugin.archiver;

import java.io.File;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;

public interface ArchiverHelper {

  public void createArchive(MavenSession session, File target, boolean compress,
      Iterable<? extends ArchiverCallback> callbacks) throws MojoExecutionException;

  public ArchiverCallback fileAdder(File... files);

  public ArchiverCallback fileAdder(Map<File, String> namedFiles);

  public ArchiverCallback directoryAdder(File... directories);

  public ArchiverCallback directoryAdder(Map<File, String> directoriesWithPrefixes);

  public ArchiverCallback mergeArtifacts(File tmpDir, Iterable<? extends Artifact> artifacts);

  public ArchiverCallback manifest(Map<String, String> manifestEntries);

}