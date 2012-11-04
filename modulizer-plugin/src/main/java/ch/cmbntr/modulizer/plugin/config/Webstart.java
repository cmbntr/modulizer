package ch.cmbntr.modulizer.plugin.config;

import static com.google.common.base.Preconditions.checkState;
import static org.codehaus.plexus.util.StringUtils.isNotBlank;

import java.io.File;

import org.apache.maven.plugins.annotations.Parameter;

import com.google.common.base.Objects.ToStringHelper;

public class Webstart extends ConfigBase {

  @Parameter(defaultValue = "${basedir}/src/main/webstart")
  private File overlay;
  private String jnlpFile = "launch.jnlp";
  private String resourcesPath = "resources";
  private String classifierName = "webstart";

  @Override
  public void checkParams() {
    super.checkParams();
    checkState(isNotBlank(this.jnlpFile), "no jnlpFile specified");
    checkState(isNotBlank(this.classifierName), "no classifier specified");
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

  @Override
  protected ToStringHelper prepareToStringHelper() {
    return super.prepareToStringHelper().add("jnlpFile", this.jnlpFile).add("resourcesPath", this.resourcesPath)
        .add("classifierName", this.classifierName).add("overlay", this.overlay);
  }

}
