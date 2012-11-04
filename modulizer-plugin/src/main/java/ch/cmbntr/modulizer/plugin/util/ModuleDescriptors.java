package ch.cmbntr.modulizer.plugin.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.jdom2.DefaultJDOMFactory;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMFactory;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import ch.cmbntr.modulizer.plugin.config.ModuleDependency;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Closeables;

public class ModuleDescriptors extends ModuleDependency {

  private static final Pattern MODULE_NAME_PATTERN = Pattern
      .compile("[a-zA-Z0-9_](?:[-a-zA-Z0-9_]*[a-zA-Z0-9_])?(?:\\.[a-zA-Z0-9_](?:[-a-zA-Z0-9_]*[a-zA-Z0-9_])?)*");

  private static final Pattern MODULE_SLOT_PATTERN = Pattern.compile("[-a-zA-Z0-9_+*.]+");

  private static final Set<String> SERVICES_DISPOSITION = ImmutableSet.of("none", "import", "export");

  public static ModuleDescriptorBuilder xmlDescriptor(final String moduleName, final String slot) {
    return new ModuleDescriptorBuilder(moduleName, slot);
  }

  public static class ModuleDescriptorBuilder {

    private static final String NAMESPACE = "urn:jboss:module:1.1";

    private final JDOMFactory xml = new DefaultJDOMFactory();
    private final Element root;
    private final Document doc;

    ModuleDescriptorBuilder(final String moduleName, final String slot) {
      this.root = elem("module");
      this.doc = this.xml.document(this.root);
      setAttr(this.root, "name", true, moduleName);
      setAttr(this.root, "slot", slot != null && !"main".equals(slot), slot);
    }

    public ModuleDescriptorBuilder mainClass(final String mainClass) {
      if (mainClass != null) {
        this.root.addContent(elem("main-class").setAttribute("name", mainClass));
      }
      return this;
    }

    public ModuleDescriptorBuilder resourceRoots(final Iterable<String> paths) {
      if (paths != null) {
        final List<String> ps = ImmutableList.copyOf(paths);
        if (!ps.isEmpty()) {
          final Element resources = elem("resources");
          for (final String path : paths) {
            resources.addContent(elem("resource-root").setAttribute("path", path));
          }
          this.root.addContent(resources);
        }
      }
      return this;
    }

    public ModuleDescriptorBuilder dependencies(final Iterable<ModuleDependency> dependencies) {
      if (dependencies != null) {
        final List<ModuleDependency> deps = ImmutableList.copyOf(dependencies);
        if (!deps.isEmpty()) {
          final Element d = elem("dependencies");
          for (final ModuleDependency dep : deps) {
            final Element m = elem("module");
            setAttr(m, "name", true, dep.getName());
            setAttr(m, "slot", dep.getSlot() != null, dep.getSlot());
            setAttr(m, "optional", dep.isOptional(), "true");
            setAttr(m, "export", dep.isExport(), "true");
            setAttr(m, "services", !"none".equals(dep.getServicesDisposition()), dep.getServicesDisposition());
            d.addContent(m);
          }
          this.root.addContent(d);
        }
      }
      return this;
    }

    public ModuleDescriptorBuilder writeTo(final File target) throws IOException {
      final BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(target));
      try {
        new XMLOutputter(Format.getPrettyFormat()).output(this.doc, out);
        out.flush();
      } finally {
        Closeables.closeQuietly(out);
      }
      return this;
    }

    @Override
    public String toString() {
      return new XMLOutputter(Format.getPrettyFormat()).outputString(this.doc);
    }

    private Element elem(final String name) {
      return this.xml.element(name, NAMESPACE);
    }

    private void setAttr(final Element e, final String name, final boolean check, final String value) {
      if (check) {
        e.setAttribute(name, value);
      }
    }

  }

  public static boolean isValidModuleName(final String name) {
    return MODULE_NAME_PATTERN.matcher(name).matches();
  }

  public static boolean isValidSlotName(final String slot) {
    return MODULE_SLOT_PATTERN.matcher(slot).matches();
  }

  public static boolean isValidServiceDisposition(final String services) {
    return SERVICES_DISPOSITION.contains(services);
  }

}
