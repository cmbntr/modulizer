package ch.cmbntr.modulizer.plugin.config;

import static com.google.common.base.Preconditions.checkState;
import static org.codehaus.plexus.util.StringUtils.isNotBlank;

import java.io.File;
import java.util.Map;

import org.apache.maven.plugins.annotations.Parameter;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.ImmutableMap;

public class Webstart extends ConfigBase {

  @Parameter(defaultValue = "${basedir}/src/main/webstart")
  private File overlay;
  private String jnlpFile = "launch.jnlp";
  private String resourcesPath = "resources";
  private String classifierName = "webstart";

  private File signFile;
  private File signTemplate;

  @Override
  public void checkParams() {
    super.checkParams();
    checkState(isNotBlank(this.jnlpFile), "no jnlpFile specified");
    checkState(isNotBlank(this.classifierName), "no classifier specified");

    final boolean onlyOne = this.signFile == null || this.signTemplate == null;
    checkState(onlyOne, "only one of signFile or signTemplate allowed");
  }

  public String getClassifierName() {
    return this.classifierName;
  }

  public String getJNLPName() {
    return this.jnlpFile;
  }

  public String getResourcesPathPrefix() {
    return this.resourcesPath;
  }

  public File getOverlayDirectory() {
    return this.overlay;
  }

  public Map<File, String> determineSignIncludes() {
    if (this.signFile != null) {
      return ImmutableMap.of(this.signFile, "JNLP-INF/APPLICATION.JNLP");
    }
    if (this.signTemplate != null) {
      return ImmutableMap.of(this.signTemplate, "JNLP-INF/APPLICATION_TEMPLATE.JNLP");
    }
    return ImmutableMap.of();
  }

  @Override
  protected ToStringHelper prepareToStringHelper() {
    return super.prepareToStringHelper().add("jnlpFile", this.jnlpFile).add("resourcesPath", this.resourcesPath)
        .add("classifierName", this.classifierName).add("overlay", this.overlay).add("signFile", this.signFile)
        .add("signTemplate", this.signTemplate);
  }

}
