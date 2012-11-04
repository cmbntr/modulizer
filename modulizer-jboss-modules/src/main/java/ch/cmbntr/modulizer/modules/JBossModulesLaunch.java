package ch.cmbntr.modulizer.modules;

import static ch.cmbntr.modulizer.modules.ModulizerModulesUtil.invokeModulesMain;
import static ch.cmbntr.modulizer.modules.ModulizerModulesUtil.preloadClasses;

import java.io.File;
import java.util.Arrays;

import ch.cmbntr.modulizer.bootstrap.BootstrapContext;
import ch.cmbntr.modulizer.bootstrap.impl.AbstractLaunch;

public class JBossModulesLaunch extends AbstractLaunch {

  @Override
  public void run() {
    preloadClasses();

    final String appName = determineAppName();
    final String mainModule = determineMainModule();
    final File modulesRepo = determineModulesRepoLocation();
    final String[] args = determineArguments();
    log("starting module '%s' (repo=%s, module=%s, args=%s)", appName, modulesRepo, mainModule, Arrays.toString(args));

    invokeModulesMain(appName, modulesRepo, mainModule, args);
  }

  private String determineAppName() {
    return lookupContextWithFallback(BootstrapContext.CONFIG_KEY_APP_ID, BootstrapContext.CONFIG_KEY_MAIN_MODULE);
  }

  private String determineMainModule() {
    return lookupContextWithFallback(BootstrapContext.CONFIG_KEY_MAIN_MODULE, BootstrapContext.CONFIG_KEY_APP_ID);
  }

  private File determineModulesRepoLocation() {
    return new File(lookupContext(BootstrapContext.CONFIG_KEY_APP_DIR));
  }

  private String[] determineArguments() {
    return BootstrapContext.ARGS.get();
  }

}
