package ch.cmbntr.modulizer.plugin.config;

import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;

public class ConfigBase {

  public void checkParams() {
    // nothing to do
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return prepareToStringHelper().toString();
  }

  protected ToStringHelper prepareToStringHelper() {
    return Objects.toStringHelper(this);
  }

}
