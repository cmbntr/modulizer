package ch.cmbntr.modulizer.bootstrap.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap;
import java.util.HashSet;
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;

import ch.cmbntr.modulizer.bootstrap.BootstrapContext;

public class PropertiesContext extends AbstractMap<String, String> implements BootstrapContext {

  private final Properties props = new Properties();

  private PropertiesContext() {
    super();
  }

  public static PropertiesContext empty() {
    return new PropertiesContext();
  }

  public synchronized PropertiesContext addSystemProperties() {
    this.props.putAll(System.getProperties());
    return this;
  }

  public synchronized PropertiesContext loadFromXML(final InputStream xml) throws InvalidPropertiesFormatException,
      IOException {
    if (xml != null) {
      this.props.loadFromXML(xml);
    }
    return this;
  }

  @Override
  public synchronized String put(final String key, final String value) {
    return (String) this.props.put(key, value);
  }

  @Override
  public String get(final Object key) {
    final String val = (String) this.props.get(key);
    return val;
  }

  @Override
  public String getInterpolated(final String key) {
    final String v = get(key);
    return v == null ? null : interpolate(v, new HashSet<String>());
  }

  private String interpolate(final String expr, final Set<String> done) {
    final String resolved = interpolateFirstReferenceOrNull(expr, done);
    return resolved == null ? expr : interpolate(resolved, done);
  }

  private String interpolateFirstReferenceOrNull(final String expr, final Set<String> done) {
    final Matcher markers = INTERPOLATION.matcher(expr);
    while (markers.find()) {
      final String ref = markers.group(1);
      if (done.add(ref)) {
        final String replacement = get(ref);
        if (replacement != null) {
          return expr.replace(markers.group(0), replacement);
        }
      }
    }
    return null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public synchronized Set<java.util.Map.Entry<String, String>> entrySet() {
    @SuppressWarnings("rawtypes")
    final Set es = this.props.entrySet();
    return es;
  }

}
