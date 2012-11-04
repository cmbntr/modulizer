package ch.cmbntr.modulizer.plugin.archiver;

import org.codehaus.plexus.archiver.jar.JarArchiver;

public interface ArchiverCallback {
  public void addContents(JarArchiver archiver);
}