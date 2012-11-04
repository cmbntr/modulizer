package ch.cmbntr.modulizer.plugin;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.cli.CommandLineUtils;

import ch.cmbntr.modulizer.modules.ModulizerModulesUtil;

@Mojo(name = "run", requiresProject = false, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class ModuleRunnerMojo extends AbstractModulizeMojo {

  @Parameter(property = "args")
  private String commandlineArgs;

  @Parameter(property = "wait", defaultValue = "true")
  private boolean wait = true;

  @Override
  protected void safeExecute() throws MojoExecutionException, MojoFailureException {
    final String[] args = parseCommandlineArgs();
    getLog().info(
        String.format("starting module '%s' (repo=%s, args=%s)", this.mainModule, this.modulesDirectory,
            Arrays.toString(args)));
    invokeLauncher(this.modulesDirectory, this.mainModule, args);
  }

  private ClassLoader getLauncherLoader() throws MojoExecutionException {
    final URL[] urls = { artifactURL(findLauncherPlugin()), artifactURL(findJBossModules()) };
    return new URLClassLoader(urls);
  }

  private void invokeLauncher(final File repo, final String module, final String... args) throws MojoExecutionException {
    final Thread thread = Thread.currentThread();
    final ClassLoader loader = getLauncherLoader();
    final ClassLoader origCCL = thread.getContextClassLoader();
    try {
      thread.setContextClassLoader(loader);
      final Object[] launchParams = { repo, module, args };
      getLaunchMethod(loader, this.wait).invoke(null, launchParams);
    } catch (final IllegalAccessException e) {
      failInvokeLaunchMethod(e.getCause());
    } catch (final IllegalArgumentException e) {
      failInvokeLaunchMethod(e.getCause());
    } catch (final InvocationTargetException e) {
      failInvokeLaunchMethod(e.getCause());
    } finally {
      thread.setContextClassLoader(origCCL);
    }
  }

  public static Method getLaunchMethod(final ClassLoader loader, final boolean wait) throws MojoExecutionException {
    try {
      return ModulizerModulesUtil.getLaunchMethod(loader, wait);
    } catch (final ClassNotFoundException e) {
      throw failGetLaunchMethod(e);
    } catch (final NoSuchMethodException e) {
      throw failGetLaunchMethod(e);
    } catch (final SecurityException e) {
      throw failGetLaunchMethod(e);
    }
  }

  private static MojoExecutionException failGetLaunchMethod(final Throwable cause) throws MojoExecutionException {
    throw new MojoExecutionException("could not find launch method", cause);
  }

  private void failInvokeLaunchMethod(final Throwable cause) throws MojoExecutionException {
    throw new MojoExecutionException("launch method invocation failed", cause);
  }

  private String[] parseCommandlineArgs() throws MojoExecutionException {
    try {
      return this.commandlineArgs == null ? null : CommandLineUtils.translateCommandline(this.commandlineArgs);
    } catch (final Exception e) {
      throw new MojoExecutionException(e.getMessage());
    }
  }

}
