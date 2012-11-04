package ch.cmbntr.modulizer.plugin.config;

import static com.google.common.base.Preconditions.checkState;
import static org.codehaus.plexus.util.StringUtils.isNotBlank;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.jarsigner.JarSignerSignRequest;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.Sets;

public class SigningInfo extends ConfigBase {

  private static final String PASSWORD_MASK = "*****";

  private String alias;
  private String keypass;
  private String storetype = "pkcs12";
  private String keystore;
  private String storepass;

  private String[] arguments;
  private Properties signRequestProperties = new Properties();

  @Override
  public void checkParams() {
    super.checkParams();
    checkState(isNotBlank(this.alias), "no alias specified");
    checkState(isNotBlank(this.keystore), "no keystore specified");
    try {
      final File sanitized = new File(this.keystore).getCanonicalFile().getAbsoluteFile();
      checkState(sanitized.canRead(), "keystore not readable at " + sanitized);
      this.keystore = sanitized.getAbsolutePath();
    } catch (final IOException e) {
      throw new IllegalArgumentException("problem with the keystore file", e);
    }
  }

  public JarSignerSignRequest signRequest(final File workingDirectory, final File archive)
      throws MojoExecutionException {

    final JarSignerSignRequest request = new JarSignerSignRequest();
    request.setWorkingDirectory(workingDirectory);
    request.setArchive(archive);

    request.setAlias(this.alias);
    request.setKeypass(this.keypass);
    request.setStoretype(this.storetype);
    request.setKeystore(this.keystore);
    request.setStorepass(this.storepass);
    request.setArguments(this.arguments);
    setBeanProperties(request);
    return request;
  }

  private void setBeanProperties(final JarSignerSignRequest request) throws MojoExecutionException {
    if (!this.signRequestProperties.isEmpty()) {
      try {

        final Set<?> props = Sets.newHashSet(this.signRequestProperties.keySet());
        final BeanInfo i = Introspector.getBeanInfo(JarSignerSignRequest.class);
        for (final PropertyDescriptor p : i.getPropertyDescriptors()) {
          final String name = p.getName();
          if (props.remove(name)) {
            p.getWriteMethod().invoke(request, this.signRequestProperties.getProperty(name));
          }
        }
        if (!props.isEmpty()) {
          throw new MojoExecutionException("the following properties have illegal names: " + props);
        }
      } catch (final IllegalAccessException e) {
        failSetProperty(e);
      } catch (final IllegalArgumentException e) {
        failSetProperty(e);
      } catch (final InvocationTargetException e) {
        failSetProperty(e);
      } catch (final IntrospectionException e) {
        failSetProperty(e);
      }
    }
  }

  private void failSetProperty(final Exception e) throws MojoExecutionException {
    throw new MojoExecutionException("JarSignerSignRequest property setting failed", e);
  }

  @Override
  protected ToStringHelper prepareToStringHelper() {
    return super.prepareToStringHelper().add("alias", this.alias)
        .add("keypass", this.keypass == null ? null : PASSWORD_MASK).add("keystore", this.keystore)
        .add("storepass", this.storepass == null ? null : PASSWORD_MASK).add("arguments", this.arguments)
        .add("signRequestProperties", this.signRequestProperties);
  }

}
