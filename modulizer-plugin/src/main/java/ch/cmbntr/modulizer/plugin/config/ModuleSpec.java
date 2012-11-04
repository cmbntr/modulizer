package ch.cmbntr.modulizer.plugin.config;

import static ch.cmbntr.modulizer.plugin.util.ModuleDescriptors.isValidModuleName;
import static ch.cmbntr.modulizer.plugin.util.ModuleDescriptors.isValidSlotName;
import static com.google.common.base.Preconditions.checkState;
import static org.codehaus.plexus.util.StringUtils.isNotBlank;

import java.io.File;
import java.util.List;

import org.apache.maven.artifact.Artifact;

import ch.cmbntr.modulizer.plugin.util.ModuleDescriptors;
import ch.cmbntr.modulizer.plugin.util.ModuleDescriptors.ModuleDescriptorBuilder;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class ModuleSpec extends ConfigBase {

  private String name;

  private String slot = "main";

  private String mainClass;

  private ModuleResources moduleResources;

  private List<ModuleDependency> dependencies = ImmutableList.of();

  @Override
  public void checkParams() {
    super.checkParams();
    checkState(isValidModuleName(this.name), "invalid module name: %s", this.name);
    checkState(isValidSlotName(this.slot), "invalid slot name: %s", this.slot);
    if (this.mainClass != null) {
      checkState(isNotBlank(this.mainClass));
    }

    if (this.moduleResources == null) {
      this.moduleResources = new ModuleResources();
    } else {
      this.moduleResources.checkParams();
    }

    for (final ModuleDependency dep : this.dependencies) {
      dep.checkParams();
    }
  }

  public ModuleDescriptorBuilder createDescriptor(final Iterable<String> resourceRootPaths) {
    final ModuleDescriptorBuilder desc = ModuleDescriptors.xmlDescriptor(this.name, this.slot);
    desc.mainClass(this.mainClass);
    desc.resourceRoots(resourceRootPaths);
    desc.dependencies(this.dependencies);
    return desc;
  }

  public StringBuilder getDirectoryPath() {
    final StringBuilder path = new StringBuilder(40);
    path.append(this.name.replace('.', File.separatorChar));
    path.append(File.separatorChar).append(this.slot);
    path.append(File.separatorChar);
    return path;
  }

  public String getModuleIdentifier() {
    return this.name + ":" + this.slot;
  }

  public Iterable<Artifact> determineModuleArtifacts(final Iterable<Artifact> allArtifacts) {
    return Iterables.filter(allArtifacts, this.moduleResources.getArtifactSelector());
  }

  @Override
  protected ToStringHelper prepareToStringHelper() {
    return super.prepareToStringHelper().add("name", this.name).add("slot", this.slot).add("mainClass", this.mainClass)
        .add("moduleResources", this.moduleResources).add("dependencies", this.dependencies);
  }

}
