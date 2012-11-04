package ch.cmbntr.modulizer.plugin.config;

import static ch.cmbntr.modulizer.plugin.util.ModuleDescriptors.isValidModuleName;
import static ch.cmbntr.modulizer.plugin.util.ModuleDescriptors.isValidServiceDisposition;
import static ch.cmbntr.modulizer.plugin.util.ModuleDescriptors.isValidSlotName;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Objects.ToStringHelper;

public class ModuleDependency extends ConfigBase {

  private String name;

  private String slot;

  private String services = "none";

  private boolean export = false;

  private boolean optional = false;

  public ModuleDependency() {
    super();
  }

  protected ModuleDependency(final String name, final String slot, final boolean optional, final boolean export,
      final String services) {
    this();
    this.name = name;
    this.slot = slot;
    this.services = services;
    this.export = export;
    this.optional = optional;
  }

  public static ModuleDependency to(final String name) {
    return new ModuleDependency(name, null, false, false, "none");
  }

  public static ModuleDependency to(final String name, final String slot, final boolean optional, final boolean export,
      final String services) {
    return new ModuleDependency(name, slot, optional, export, services);
  }

  @Override
  public void checkParams() {
    super.checkParams();
    checkState(isValidModuleName(this.name), "invalid module name: %s", this.name);
    if (this.slot != null) {
      checkState(isValidSlotName(this.slot), "invalid slot name: %s", this.slot);
    }
    checkState(isValidServiceDisposition(this.services), "invalid services dispostition: %s", this.services);
  }

  public String getName() {
    return this.name;
  }

  public String getSlot() {
    return this.slot;
  }

  public String getServicesDisposition() {
    return this.services;
  }

  public boolean isExport() {
    return this.export;
  }

  public boolean isOptional() {
    return this.optional;
  }

  @Override
  protected ToStringHelper prepareToStringHelper() {
    return super.prepareToStringHelper().add("name", this.name).add("slot", this.slot).add("services", this.services)
        .add("export", this.export).add("optional", this.optional);
  }

}
