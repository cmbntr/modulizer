package ch.cmbntr.modulizer.plugin;

import static ch.cmbntr.modulizer.bootstrap.util.SystemPropertyHelper.restoreProps;
import static ch.cmbntr.modulizer.bootstrap.util.SystemPropertyHelper.snapshotProps;
import static ch.cmbntr.modulizer.plugin.util.ArtifactInfo.createFilter;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.find;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import ch.cmbntr.modulizer.bootstrap.util.Resources;
import ch.cmbntr.modulizer.bootstrap.util.XMLFactories;
import ch.cmbntr.modulizer.bootstrap.util.Resources.Pool;
import ch.cmbntr.modulizer.plugin.util.ArtifactPattern;

import com.google.common.base.Predicate;

public abstract class AbstractModulizeMojo extends AbstractMojo {

  @SuppressWarnings("unchecked")
  private static final Predicate<Artifact> IS_MODULIZER_BOOTSTRAP = createFilter(ArtifactPattern
      .valueOf("*:modulizer-bootstrap:jar:*:*"));

  @SuppressWarnings("unchecked")
  private static final Predicate<Artifact> IS_PREPARE_PLUGIN = createFilter(ArtifactPattern
      .valueOf("*:modulizer-filetree:jar:plugin:*"));

  @SuppressWarnings("unchecked")
  private static final Predicate<Artifact> IS_LAUNCHER_PLUGIN = createFilter(ArtifactPattern
      .valueOf("*:modulizer-jboss-modules:jar:plugin:*"));

  @SuppressWarnings("unchecked")
  private static final Predicate<Artifact> IS_JBOSS_MODULES = createFilter(ArtifactPattern
      .valueOf("org.jboss.modules:jboss-modules:jar:*:*"));

  @Parameter(property = "module", required = true)
  protected String mainModule;

  @Parameter(defaultValue = "${project.build.directory}/modules", required = true)
  protected File modulesDirectory;

  @Component
  private MavenProject proj;

  @Parameter(defaultValue = "${plugin.artifacts}", readonly = true)
  private List<Artifact> pluginDependencies;

  @Parameter(defaultValue = "false")
  private boolean brokenXMLFactoriesHack;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    final Pool handle = Resources.getPoolHandle();
    final Thread currentThread = Thread.currentThread();
    final ClassLoader origCCL = currentThread.getContextClassLoader();
    final Properties origProps = snapshotProps();
    try {
      brokenXMLFactoriesHack();
      checkParams();
      safeExecute();
    } finally {
      currentThread.setContextClassLoader(origCCL);
      restoreProps(origProps);
      Resources.dispose(handle);
    }
  }

  private void brokenXMLFactoriesHack() {
    if (!this.brokenXMLFactoriesHack) {
      return;
    }
    final Log log = getLog();
    try {
      log.debug("XMLFactories: " + XMLFactories.inspectXMLFactories());
    } catch (final Throwable t) {
      log.error("broken XMLFactories", t);
      try {
        log.info("trying fallbacks...");
        XMLFactories.installFallbacks();
        log.error("fallbacks work: " + XMLFactories.inspectXMLFactories());
      } catch (final Throwable tt) {
        log.error("fallbacks did not work", tt);
      }
    }
  }

  protected void checkParams() {
    checkNotNull(this.mainModule, "invalid mainModule");
    checkNotNull(this.modulesDirectory, "invalid modulesDirectory");
  }

  protected Artifact findDependency(final Predicate<Artifact> test) throws NoSuchElementException {
    return find(concat(this.proj.getDependencyArtifacts(), this.proj.getArtifacts(), this.pluginDependencies), test);
  }

  protected Artifact findModulizerBootstrap() {
    return findDependency(IS_MODULIZER_BOOTSTRAP);
  }

  protected Artifact findPreparePlugin() {
    return findDependency(IS_PREPARE_PLUGIN);
  }

  protected Artifact findLauncherPlugin() {
    return findDependency(IS_LAUNCHER_PLUGIN);
  }

  protected Artifact findJBossModules() {
    return findDependency(IS_JBOSS_MODULES);
  }

  protected URL artifactURL(final Artifact dep) throws MojoExecutionException {
    try {
      final File f = dep.getFile();
      if (f == null) {
        throw new MojoExecutionException(dep + " has no file");
      }
      return f.toURI().toURL();
    } catch (final MalformedURLException e) {
      throw new MojoExecutionException("failed to get URL for " + dep);
    }
  }

  protected abstract void safeExecute() throws MojoExecutionException, MojoFailureException;

}
