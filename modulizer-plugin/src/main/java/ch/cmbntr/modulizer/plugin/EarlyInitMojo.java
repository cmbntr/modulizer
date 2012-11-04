package ch.cmbntr.modulizer.plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jboss.modules.LocalModuleLoader;
import org.jboss.modules.Module;

import ch.cmbntr.modulizer.bootstrap.util.SystemPropertyHelper;
import ch.cmbntr.modulizer.bootstrap.util.XMLFactories;

@Mojo(name = "early", defaultPhase = LifecyclePhase.VALIDATE)
public class EarlyInitMojo extends AbstractMojo {

  public static final Map<String, String> FACTORIES = new HashMap<String, String>();

  @Parameter(defaultValue = "true")
  private boolean inspectXMLFactories;

  @Parameter(defaultValue = "false")
  private boolean jaxpDebug;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    xmlFactoriesInspection();
    jbossModulesPreloading();
  }

  private void xmlFactoriesInspection() {
    final Log log = getLog();
    if (this.inspectXMLFactories) {
      final Properties orig = SystemPropertyHelper.snapshotProps();
      try {
        log.debug(orig.toString());
        if (this.jaxpDebug) {
          //might be too late though, since FactoryFinders are initialized only once
          System.setProperty("jaxp.debug", "true");
        }
        FACTORIES.putAll(XMLFactories.inspectXMLFactories());
      } catch (final Throwable t) {
        log.error("could not inspect XML factories, build environment is broken", t);
      } finally {
        SystemPropertyHelper.restoreProps(orig);
      }
      log.debug(FACTORIES.toString());
    }
  }

  private void jbossModulesPreloading() {
    final Log log = getLog();
    log.debug(Module.class.toString());
    log.debug(LocalModuleLoader.class.toString());
  }

}
