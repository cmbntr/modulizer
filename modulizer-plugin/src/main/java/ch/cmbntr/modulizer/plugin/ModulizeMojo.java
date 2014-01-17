package ch.cmbntr.modulizer.plugin;

import static ch.cmbntr.modulizer.bootstrap.BootstrapContext.CONFIG_KEY_APP_ID;
import static ch.cmbntr.modulizer.bootstrap.BootstrapContext.CONFIG_KEY_BUNDLE_ID;
import static ch.cmbntr.modulizer.bootstrap.BootstrapContext.CONFIG_KEY_BUNDLE_REF;
import static ch.cmbntr.modulizer.bootstrap.BootstrapContext.CONFIG_KEY_BUNDLE_URI;
import static ch.cmbntr.modulizer.bootstrap.BootstrapContext.CONFIG_KEY_LAUNCH_PLUGINS;
import static ch.cmbntr.modulizer.bootstrap.BootstrapContext.CONFIG_KEY_MAIN_MODULE;
import static ch.cmbntr.modulizer.bootstrap.BootstrapContext.CONFIG_KEY_PREPARE_PLUGINS;
import static ch.cmbntr.modulizer.bootstrap.BootstrapContext.DEFAULT_BUNDLE_REF;
import static ch.cmbntr.modulizer.bootstrap.BootstrapContext.DEFAULT_BUNDLE_URI;
import static ch.cmbntr.modulizer.bootstrap.util.ModulizerIO.sha1async;
import static ch.cmbntr.modulizer.plugin.util.ModulizerUtil.collect;
import static ch.cmbntr.modulizer.plugin.util.ModulizerUtil.compute;
import static ch.cmbntr.modulizer.plugin.util.ModulizerUtil.computeLazyAsync;
import static ch.cmbntr.modulizer.plugin.util.ModulizerUtil.mkdir;
import static ch.cmbntr.modulizer.plugin.util.ModulizerUtil.sha1Name;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Iterables.concat;
import static java.lang.String.format;
import static org.codehaus.plexus.util.StringUtils.isEmpty;
import static org.codehaus.plexus.util.StringUtils.isNotBlank;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.jarsigner.JarSigner;
import org.apache.maven.shared.jarsigner.JarSignerException;
import org.apache.maven.shared.jarsigner.JarSignerRequest;
import org.apache.maven.shared.jarsigner.JarSignerResult;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.WriterFactory;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.jboss.modules.Module;

import ch.cmbntr.modulizer.bootstrap.Main;
import ch.cmbntr.modulizer.bootstrap.util.ModulizerIO;
import ch.cmbntr.modulizer.bootstrap.util.Resources;
import ch.cmbntr.modulizer.bootstrap.util.Resources.Pool;
import ch.cmbntr.modulizer.filetree.Snapshot;
import ch.cmbntr.modulizer.filetree.Snapshot.FileTreeSnapshotException;
import ch.cmbntr.modulizer.modules.ModulizerModulesUtil;
import ch.cmbntr.modulizer.plugin.archiver.ArchiverCallback;
import ch.cmbntr.modulizer.plugin.archiver.ArchiverHelper;
import ch.cmbntr.modulizer.plugin.config.ModuleSpec;
import ch.cmbntr.modulizer.plugin.config.SigningInfo;
import ch.cmbntr.modulizer.plugin.config.Webstart;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ComputationException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;

@Mojo(name = "modulize", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.TEST)
public class ModulizeMojo extends AbstractModulizeMojo {

  private static final String RESOURCES_DEF_FRAGMENT_TEMPLATE = "<%%-- resources_def.jspf --%%><%%@ page pageEncoding=\"UTF-8\" %%>\n<%% final java.util.List<String> resources = java.util.Arrays.asList(%s); %%>";

  private static final String WEBSTART_WEB_XML = "WEB-INF/web.xml";

  private static final String WEBSTART_JNLP_TEMPLATE = "template.jnlp";

  private static final String WEBSTART_JNLP_BODY = "WEB-INF/jspf/jnlp/body.jspf";

  private static final String WEBSTART_RESOURCES_DEF_FRAGMENT = "WEB-INF/jspf/jnlp/resources_def.jspf";

  private static final String WEBSTART_RESOURCES_FRAGMENT = "WEB-INF/jspf/jnlp/resources.jspf";

  private static final String WEBSTART_RESOURCE_FRAGMENT = "WEB-INF/jspf/jnlp/resource.jspf";

  private static final List<String> WEBSTART_DEFAULT_FILES = ImmutableList.of(WEBSTART_WEB_XML, WEBSTART_JNLP_BODY,
      WEBSTART_RESOURCES_FRAGMENT, WEBSTART_RESOURCE_FRAGMENT, WEBSTART_RESOURCES_DEF_FRAGMENT);

  @Component
  private MavenProject project;

  @Component
  private MavenProjectHelper projectHelper;

  @Component
  private ArchiverHelper archiver;

  @Component
  private MavenSession session;

  @Component
  private JarSigner jarSigner;

  @Parameter(defaultValue = "false")
  private boolean verbose = false;

  @Parameter(defaultValue = "${project.build.directory}")
  private File outputDirectory;

  @Parameter
  private SigningInfo signing;

  @Parameter
  private Webstart webstart;

  @Parameter(defaultValue = "${project.name}")
  private String applicationName;

  @Parameter(defaultValue = "launcher", required = true)
  private String launcherClassifierName;

  @Parameter(defaultValue = "modules")
  private String modulesClassifierName;

  @Parameter(defaultValue = "false")
  private boolean includeModulesInLauncher = false;

  @Parameter(defaultValue = "${basedir}/src/main/modules")
  private File overlayDirectory;

  @Parameter
  private Properties launcherManifest = new Properties();

  @Parameter
  private Properties bootstrapContext = new Properties();

  @Parameter
  private List<ModuleSpec> modules = ImmutableList.of();

  @Parameter(defaultValue = "true")
  private boolean warmupModules = true;

  @Parameter(defaultValue = "true")
  private boolean warmupMandatory = true;

  @Parameter(defaultValue = "true")
  private boolean stripVersion = true;

  private String conf(final String key, final String defaultValue) {
    return this.bootstrapContext.getProperty(key, defaultValue);
  }

  private void augmentContext(final String msg, final Map<String, String> bootstrapInfo) {
    log(msg + bootstrapInfo);
    this.bootstrapContext.putAll(bootstrapInfo);
  }

  private List<String> getSpecifiedModuleIdentifiers() {
    final Builder<String> ids = ImmutableList.builder();
    for (final ModuleSpec module : this.modules) {
      ids.add(module.getModuleIdentifier());
    }
    return ids.build();
  }

  private String determineApplicationName() {
    if (this.applicationName == null) {
      final String n = this.project.getName();
      return n == null ? "Modulized Application" : n;
    }
    return this.applicationName;
  }

  private String determineLauncherArtifactName() {
    return buildArtifactName(this.launcherClassifierName, "jar");
  }

  private String determineModulesArtifactName() {
    return buildArtifactName(this.modulesClassifierName, "jar");
  }

  private String determineWebstartArtifactName() {
    return buildArtifactName(this.webstart.getClassifierName(), "war");
  }

  private String buildArtifactName(final String classifier, final String externsion) {
    final Artifact artifact = this.project.getArtifact();
    final String artifactId = artifact.getArtifactId();
    final String version = artifact.getVersion();
    return String.format("%s-%s-%s.%s", artifactId, version, classifier, externsion);
  }

  @Override
  protected void checkParams() {
    super.checkParams();
    checkState(isNotBlank(this.modulesClassifierName), "invalid classifier");
    final Set<String> moduleIdentifiers = Sets.newHashSet();
    for (final ModuleSpec module : this.modules) {
      final String id = module.getModuleIdentifier();
      checkState(moduleIdentifiers.add(id), "duplicate module identifier: %s", id);
      module.checkParams();
    }
    if (this.signing != null) {
      this.signing.checkParams();
    }
    if (this.webstart != null) {
      this.webstart.checkParams();
    }
    augmentContext(
        "main module: ",
        ImmutableMap.of(CONFIG_KEY_MAIN_MODULE, this.mainModule, CONFIG_KEY_APP_ID,
            conf(CONFIG_KEY_APP_ID, this.mainModule)));
  }

  @Override
  protected void safeExecute() throws MojoExecutionException {
    createModulesDirectory();
    createModules();
    copyModulesOverlay();
    warmupModules();

    final List<File> artifacts = createArtifacts();
    sign(artifacts);
    webstart(artifacts);
  }

  private void createModulesDirectory() throws MojoExecutionException {
    log("create modules directory");
    mkdir(this.modulesDirectory);
  }

  private void createModules() throws MojoExecutionException {
    try {
      final Map<ModuleSpec, File> lazyModules = computeLazyAsync(this.modules, createModule());
      for (final Entry<ModuleSpec, File> forcedModule : lazyModules.entrySet()) {
        final String id = forcedModule.getKey().getModuleIdentifier();
        final File descriptor = forcedModule.getValue();
        log(String.format("create module %s -> %s", id, descriptor));
      }
    } catch (final ComputationException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof MojoExecutionException) {
        throw (MojoExecutionException) cause;
      } else {
        throw new MojoExecutionException("create modules failed", cause);
      }
    }
  }

  private Function<ModuleSpec, File> createModule() {
    final Set<Artifact> artifacts = collectAllArtifacts();
    return new Function<ModuleSpec, File>() {
      @Override
      public File apply(final ModuleSpec spec) {
        try {
          final File dir = createModuleDirectory(spec);
          final Iterable<String> resources = copyArtifacts(dir, spec.determineModuleArtifacts(artifacts));
          return createModuleDescriptor(spec, dir, resources);
        } catch (final MojoExecutionException e) {
          throw new ComputationException(e);
        }
      }
    };
  }

  private synchronized Set<Artifact> collectAllArtifacts() {
    final ImmutableSet.Builder<Artifact> allArtifacts = ImmutableSet.builder();
    allArtifacts.add(this.project.getArtifact());
    // NOTE: #getArtifacts is lazy and not thread-safe
    allArtifacts.addAll(this.project.getArtifacts());
    return allArtifacts.build();
  }

  private File createModuleDirectory(final ModuleSpec module) throws MojoExecutionException {
    final File dir = new File(this.modulesDirectory, module.getDirectoryPath().toString());
    mkdir(dir);
    return dir;
  }

  private Iterable<String> copyArtifacts(final File moduleDirectory, final Iterable<Artifact> artifacts)
      throws MojoExecutionException {

    final Map<Artifact, File> fromTo = compute(artifacts, resourceTargetFile(moduleDirectory));
    final Map<File, Future<String>> targetHashes = sha1async(fromTo.values());

    final Pool pool = Resources.getPoolHandle();
    final ExecutorService executor = pool.aquireExec();
    try {
      return collect(ImmutableList.copyOf(transform(fromTo.entrySet(),
          new Function<Entry<Artifact, File>, Future<String>>() {
            @Override
            public Future<String> apply(final Entry<Artifact, File> e) {
              final Artifact a = e.getKey();
              final File src = a.getFile();
              final File target = e.getValue();
              final Future<String> targetHash = targetHashes.get(target);
              checkState(src != null, "missing file for %s", a);
              return executor.submit(conditionalCopy(src, target, targetHash));
            }
          })));
    } catch (final ComputationException e) {
      throw new MojoExecutionException("failed to copy artifacts", e.getCause());
    } finally {
      pool.releaseExec(executor);
    }

  }

  private Function<Artifact, File> resourceTargetFile(final File moduleDirectory) {
    final boolean removeVersion = this.stripVersion;
    return new Function<Artifact, File>() {
      @Override
      public File apply(final Artifact resource) {
        final String version = removeVersion ? "" : "-" + resource.getVersion();
        final String classifier = isEmpty(resource.getClassifier()) ? "" : "-" + resource.getClassifier();
        final String extension = resource.getArtifactHandler().getExtension();
        final String name = format("%s-%s%s%s.%s", resource.getGroupId(), resource.getArtifactId(), version,
            classifier, extension);
        return new File(moduleDirectory, name);
      }
    };
  }

  private File createModuleDescriptor(final ModuleSpec module, final File moduleDirectory,
      final Iterable<String> resources) throws MojoExecutionException {
    try {
      final File descriptor = new File(moduleDirectory, "module.xml");
      module.createDescriptor(resources).writeTo(descriptor);
      return descriptor;
    } catch (final IOException e) {
      throw new MojoExecutionException("failed to write module descriptor", e);
    }
  }

  private void copyModulesOverlay() throws MojoExecutionException {
    copyOverlay("modules", this.overlayDirectory, this.modulesDirectory);
  }

  private void warmupModules() {
    if (this.warmupModules) {
      final List<String> ids = getSpecifiedModuleIdentifiers();
      for (final Module module : ModulizerModulesUtil.tryLoadModules(this.warmupMandatory, ids, this.modulesDirectory)) {
        log("loaded " + module.getIdentifier());
      }
    }
  }

  private File createBundle() throws MojoExecutionException {
    try {
      log("create filetree");
      final String ref = determineBundleRef();
      final String bundleName = determineBundleName();
      final String bundleURI = "/" + bundleName;
      final File bundle = new File(this.outputDirectory, bundleName);
      final String id = Snapshot.createBundle(bundle, this.modulesDirectory, ref);
      augmentContext("filetree bootstrap properties: ",
          ImmutableMap.of(CONFIG_KEY_BUNDLE_ID, id, CONFIG_KEY_BUNDLE_REF, ref, CONFIG_KEY_BUNDLE_URI, bundleURI));
      return bundle;
    } catch (final FileTreeSnapshotException e) {
      throw new MojoExecutionException("failed to create bundle", e);
    }
  }

  private String determineBundleName() {
    return new File(conf(CONFIG_KEY_BUNDLE_URI, DEFAULT_BUNDLE_URI)).getName().trim();
  }

  private String determineBundleRef() {
    return Snapshot.sanitizeHeadRef(conf(CONFIG_KEY_BUNDLE_REF, DEFAULT_BUNDLE_REF));
  }

  private File createBootstrapConfig() throws MojoExecutionException {
    final File config = new File(this.outputDirectory, "bootstrap-config.xml");
    FileOutputStream out = null;
    try {
      out = new FileOutputStream(config);
      this.bootstrapContext.storeToXML(out, null);
      return config;

    } catch (final IOException e) {
      throw new MojoExecutionException("failed to write boostrap-config.xml", e);
    } finally {
      Closeables.closeQuietly(out);
    }
  }

  private List<File> createArtifacts() throws MojoExecutionException {

    final List<ArchiverCallback> launcherArtifactContents = launcherContents();
    final List<ArchiverCallback> modulesArtifactContents = modulesContents();

    final List<File> artifacts = Lists.newLinkedList();
    final String launcherName = determineLauncherArtifactName();
    if (this.includeModulesInLauncher) {
      artifacts.add(createArtifact(launcherName, "jar", this.launcherClassifierName,
          concat(launcherArtifactContents, modulesArtifactContents)));
    } else {
      artifacts.add(createArtifact(launcherName, "jar", this.launcherClassifierName, launcherArtifactContents));
      artifacts.add(createArtifact(determineModulesArtifactName(), "jar", this.modulesClassifierName,
          modulesArtifactContents));
    }
    return artifacts;
  }

  private File createArtifact(final String name, final String type, final String classifier,
      final Iterable<? extends ArchiverCallback> contents) throws MojoExecutionException {
    final File target = new File(this.outputDirectory, name);
    log("create artifact " + target);
    this.archiver.createArchive(this.session, target, false, contents);
    this.projectHelper.attachArtifact(this.project, type, classifier, target);
    return target;
  }

  private List<ArchiverCallback> launcherContents() throws MojoExecutionException {

    final List<ArchiverCallback> contents = Lists.newLinkedList();

    // merge bootstrap artifacts into the launcher
    final List<Artifact> merges = determineLauncherMergeArtifacts();
    log("launcher merge: " + merges);
    contents.add(this.archiver.mergeArtifacts(this.outputDirectory, merges));

    // add the bootstrap plugins
    final Map<File, String> plugins = determineLauncherPlugins();
    log("launcher plugins: " + plugins);
    contents.add(this.archiver.fileAdder(plugins));

    // add the launcher manifest entries
    final Map<String, String> entries = determineLauncherManifest();
    log("launcher manifest: " + entries);
    contents.add(this.archiver.manifest(entries));

    return contents;
  }

  private List<Artifact> determineLauncherMergeArtifacts() {
    return ImmutableList.of(findJBossModules(), findModulizerBootstrap());
  }

  private Map<File, String> determineLauncherPlugins() throws MojoExecutionException {
    final File prepare = findPreparePlugin().getFile();
    final String prepareName = sha1Name(prepare);

    final File launcher = findLauncherPlugin().getFile();
    final String launcherName = sha1Name(launcher);

    augmentContext("bootstrap plugins: ",
        ImmutableMap.of(CONFIG_KEY_PREPARE_PLUGINS, prepareName, CONFIG_KEY_LAUNCH_PLUGINS, launcherName));

    return ImmutableMap.of(prepare, prepareName, launcher, launcherName);
  }

  private Map<String, String> determineLauncherManifest() {
    final Map<String, String> entries = Maps.newLinkedHashMap();
    entries.put("Main-Class", Main.class.getName());
    entries.put("Application-Name", determineApplicationName());
    entries.put("Permissions", "all-permissions");
    entries.put("Codebase", "*");
    if (!this.includeModulesInLauncher) {
      entries.put("Class-Path", determineModulesArtifactName());
    }
    for (final Entry<Object, Object> e : this.launcherManifest.entrySet()) {
      entries.put(e.getKey().toString(), e.getValue().toString());
    }
    return entries;
  }

  private List<ArchiverCallback> modulesContents() throws MojoExecutionException {
    final File bundle = createBundle();
    final File bootstrapConfig = createBootstrapConfig();
    return ImmutableList.of(this.archiver.fileAdder(bundle, bootstrapConfig));
  }

  private void sign(final Iterable<File> artifacts) throws MojoExecutionException {
    if (this.signing != null) {
      log("signing: " + this.signing);
      for (final File a : artifacts) {
        final JarSignerRequest request = this.signing.signRequest(this.outputDirectory, a);

        try {
          final JarSignerResult result = this.jarSigner.execute(request);
          final CommandLineException signException = result.getExecutionException();
          final int signExitCode = result.getExitCode();
          if (signException != null) {
            failSignature(signException);
          }
          if (signExitCode > 0) {
            throw new MojoExecutionException("signing returned with exit code: " + signExitCode);
          }
        } catch (final JarSignerException e) {
          failSignature(e);
        }
      }
    }
  }

  private void failSignature(final Exception e) throws MojoExecutionException {
    throw new MojoExecutionException("signing failed", e);
  }

  private void webstart(final Iterable<File> webstartResources) throws MojoExecutionException {
    if (this.webstart != null) {
      log("webstart: " + this.webstart);
      final File webstartDir = new File(this.outputDirectory, "webstart");
      mkdir(webstartDir);
      copyURLs(findWebstartDefaultResources(webstartDir));
      copyWebstartResourcesAndCreateDefinitionFragment(webstartDir, webstartResources);
      copyOverlay("webstart", this.webstart.getOverlayDirectory(), webstartDir);
      createWebstartArtifact(webstartDir);
    }
  }

  private Map<URL, File> findWebstartDefaultResources(final File webstartDir) throws MojoExecutionException {
    final ImmutableMap.Builder<URL, File> resources = ImmutableMap.builder();
    for (final String f : WEBSTART_DEFAULT_FILES) {
      resources.put(findWebstartDefaultResource(f), new File(webstartDir, f));
    }

    final File jnlpFile = new File(webstartDir, this.webstart.getJNLPName());
    resources.put(findWebstartDefaultResource(WEBSTART_JNLP_TEMPLATE), jnlpFile);

    return resources.build();
  }

  private URL findWebstartDefaultResource(final String file) throws MojoExecutionException {
    final URL r = ModulizeMojo.class.getResource("/webstart/" + file);
    if (r == null) {
      throw new MojoExecutionException("could not find webstart default resource: " + file);
    }
    return r;
  }

  private void copyURLs(final Map<URL, File> sourcesAndDestinations) throws MojoExecutionException {
    for (final Entry<URL, File> srcAndDest : sourcesAndDestinations.entrySet()) {
      final URL src = srcAndDest.getKey();
      final File dest = srcAndDest.getValue();
      try {
        FileUtils.copyURLToFile(src, dest);
      } catch (final IOException e) {
        throw new MojoExecutionException("could not copy " + src + " to " + dest, e);
      }
    }
  }

  private void copyWebstartResourcesAndCreateDefinitionFragment(final File webstartDir, final Iterable<File> resources)
      throws MojoExecutionException {
    try {
      final String pathPrefix = this.webstart.getResourcesPathPrefix();
      final File resourcesDir = new File(webstartDir, pathPrefix);
      final List<String> resourceHREFs = Lists.newLinkedList();
      for (final File r : resources) {
        FileUtils.copyFileToDirectory(r, resourcesDir);
        resourceHREFs.add(String.format("\"%s/%s\"", pathPrefix, r.getName()));
      }

      final Writer w = WriterFactory.newWriter(new File(webstartDir, WEBSTART_RESOURCES_DEF_FRAGMENT),
          WriterFactory.UTF_8);
      try {
        w.write(format(RESOURCES_DEF_FRAGMENT_TEMPLATE, Joiner.on(", ").join(resourceHREFs)));
      } finally {
        ModulizerIO.closeQuietly(w);
      }
    } catch (final UnsupportedEncodingException e) {
      failCopyWebstartResourcesAndCreateDefinitionFragment(e);
    } catch (final FileNotFoundException e) {
      failCopyWebstartResourcesAndCreateDefinitionFragment(e);
    } catch (final IOException e) {
      failCopyWebstartResourcesAndCreateDefinitionFragment(e);
    }
  }

  private void failCopyWebstartResourcesAndCreateDefinitionFragment(final Throwable e) throws MojoExecutionException {
    throw new MojoExecutionException("failed to copy and define webstart resources", e);
  }

  private void createWebstartArtifact(final File webstartDir) throws MojoExecutionException {
    final List<ArchiverCallback> warContents = ImmutableList.of(this.archiver.directoryAdder(webstartDir));
    createArtifact(determineWebstartArtifactName(), "war", this.webstart.getClassifierName(), warContents);
  }

  private Callable<String> conditionalCopy(final File src, final File target, final Future<String> targetHash) {
    return new Callable<String>() {
      @Override
      public String call() throws Exception {
        final String th = targetHash.get();
        if (th == null) {
          copyFile(src, target);
        } else {
          final StringBuilder sourceHash = ModulizerIO.sha1(src);
          if (sourceHash == null) {
            throw new IllegalArgumentException("source not available " + src);
          }
          if (!th.equals(sourceHash.toString())) {
            copyFile(src, target);
          }
        }
        return target.getName();
      }
    };
  }

  private void copyFile(final File src, final File dest) throws MojoExecutionException {
    try {
      log("copy " + src + " to " + dest);
      FileUtils.copyFile(src, dest);
    } catch (final IOException e) {
      throw new MojoExecutionException("Error copying artifact from " + src + " to " + dest, e);
    }
  }

  private void copyOverlay(final String displayName, final File overlay, final File target)
      throws MojoExecutionException {
    if (overlay != null && overlay.isDirectory()) {
      log("copy " + displayName + " overlay from " + overlay);
      try {
        FileUtils.copyDirectoryStructure(overlay, target);
        removeUnwantedFiles(target);
      } catch (final IOException e) {
        throw new MojoExecutionException("failed to copy " + overlay.toString(), e);
      }
    }
  }

  private void removeUnwantedFiles(final File target) throws IOException {
    final DirectoryScanner ds = new DirectoryScanner();
    ds.setBasedir(target);
    ds.setIncludes(FileUtils.getDefaultExcludes());
    ds.scan();

    for (final String file : ds.getIncludedFiles()) {
      final File f = new File(target, file);
      log("delete unwanted " + f);
      final boolean deleted = f.delete();
      if (!deleted) {
        log("could not delete " + f);
      }
    }

    for (final String dir : ds.getIncludedDirectories()) {
      final File d = new File(target, dir);
      log("delete unwanted dir " + d);
      FileUtils.deleteDirectory(d);
    }
  }

  private void log(final CharSequence msg) {
    if (this.verbose) {
      getLog().info(msg);
    } else {
      getLog().debug(msg);
    }
  }

}
