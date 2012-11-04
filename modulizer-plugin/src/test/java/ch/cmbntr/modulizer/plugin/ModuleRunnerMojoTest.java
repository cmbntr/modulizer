package ch.cmbntr.modulizer.plugin;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import ch.cmbntr.modulizer.modules.ModulizerModulesUtil;

public class ModuleRunnerMojoTest {

  @Test
  public void testLaunchMethodsExist() throws Exception {
    final ClassLoader loader = ModulizerModulesUtil.class.getClassLoader();
    assertNotNull(ModulizerModulesUtil.getLaunchMethod(loader, true));
    assertNotNull(ModulizerModulesUtil.getLaunchMethod(loader, false));
  }

}
