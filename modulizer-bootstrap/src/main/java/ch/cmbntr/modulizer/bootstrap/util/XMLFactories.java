package ch.cmbntr.modulizer.bootstrap.util;

import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPathFactory;

import org.xml.sax.helpers.XMLReaderFactory;

public class XMLFactories {

  private static final String SCHEMA_FACTORY_PREFIX = "javax.xml.validation.SchemaFactory";
  private static final String XSD_FACTORY = SCHEMA_FACTORY_PREFIX + W3C_XML_SCHEMA_NS_URI;

  private static final Map<String, String> FALLBACKS;

  private static final Set<String> XML_FACTORIES;

  static {
    final Map<String, String> f = new LinkedHashMap<String, String>();
    f.put("javax.xml.parsers.DocumentBuilderFactory",
        "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl");
    f.put("javax.xml.parsers.SAXParserFactory", "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl");
    f.put("javax.xml.transform.TransformerFactory",
        "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");
    f.put("javax.xml.xpath.XPathFactory", "com.sun.org.apache.xpath.internal.jaxp.XPathFactoryImpl");
    f.put("javax.xml.stream.XMLEventFactory", "com.sun.xml.internal.stream.events.XMLEventFactoryImpl");
    f.put("javax.xml.stream.XMLInputFactory", "com.sun.xml.internal.stream.XMLInputFactoryImpl");
    f.put("javax.xml.stream.XMLOutputFactory", "com.sun.xml.internal.stream.XMLOutputFactoryImpl");
    f.put("javax.xml.datatype.DatatypeFactory", "com.sun.org.apache.xerces.internal.jaxp.datatype.DatatypeFactoryImpl");
    f.put("org.xml.sax.driver", "com.sun.org.apache.xerces.internal.parsers.SAXParser");
    f.put(XSD_FACTORY, "com.sun.org.apache.xerces.internal.jaxp.validation.XMLSchemaFactory");

    FALLBACKS = Collections.unmodifiableMap(f);
    XML_FACTORIES = FALLBACKS.keySet();
  }

  private XMLFactories() {
    super();
  }

  public static Map<String, String> inspectXMLFactories() {
    final Map<String, String> factories = new HashMap<String, String>();
    final Collection<Throwable> failures = inspect(factories);
    if (failures.isEmpty()) {
      return factories;
    } else {
      throw new RuntimeException("failed XML Factories: " + failures);
    }
  }

  public static void installFallbacks() {
    final Map<String, String> ok = new HashMap<String, String>();
    final Collection<Throwable> failures = inspect(ok);
    if (failures.isEmpty()) {
      return;
    }

    final Set<Entry<String, String>> fb = FALLBACKS.entrySet();
    for (final Entry<String, String> fallback : fb) {
      final String factory = fallback.getKey();
      if (!ok.containsKey(factory)) {
        System.setProperty(factory, fallback.getValue());
      }
    }
  }

  private static Collection<Throwable> inspect(final Map<String, String> results) {
    final Collection<Throwable> failures = new LinkedList<Throwable>();
    try {
      register(results, "javax.xml.parsers.DocumentBuilderFactory", DocumentBuilderFactory.newInstance());
    } catch (final Throwable t) {
      failures.add(t);
    }
    try {
      register(results, "javax.xml.parsers.SAXParserFactory", SAXParserFactory.newInstance());
    } catch (final Throwable t) {
      failures.add(t);
    }
    try {
      register(results, "javax.xml.transform.TransformerFactory", TransformerFactory.newInstance());
    } catch (final Throwable t) {
      failures.add(t);
    }
    try {
      register(results, "javax.xml.xpath.XPathFactory", XPathFactory.newInstance());
    } catch (final Throwable t) {
      failures.add(t);
    }
    try {
      register(results, "javax.xml.stream.XMLEventFactory", XMLEventFactory.newInstance());
    } catch (final Throwable t) {
      failures.add(t);
    }
    try {
      register(results, "javax.xml.stream.XMLInputFactory", XMLInputFactory.newInstance());
    } catch (final Throwable t) {
      failures.add(t);
    }
    try {
      register(results, "javax.xml.stream.XMLOutputFactory", XMLOutputFactory.newInstance());
    } catch (final Throwable t) {
      failures.add(t);
    }
    try {
      register(results, "javax.xml.datatype.DatatypeFactory", DatatypeFactory.newInstance());
    } catch (final Throwable t) {
      failures.add(t);
    }
    try {
      register(results, "org.xml.sax.driver", XMLReaderFactory.createXMLReader());
    } catch (final Throwable t) {
      failures.add(t);
    }
    try {
      register(results, XSD_FACTORY, SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI));
    } catch (final Throwable t) {
      failures.add(t);
    }
    return failures;
  }

  private static void register(final Map<String, String> factories, final String configKey, final Object instance) {
    if (instance != null) {
      factories.put(configKey, instance.getClass().getName());
    }
  }

  public static void clearXMLFactories() {
    clearXMLFactories(System.getProperties());
  }

  public static Properties clearXMLFactories(final Properties props) {
    for (final String factory : XML_FACTORIES) {
      clear(props, factory);
    }
    for (final Object k : props.keySet()) {
      if (k.toString().startsWith(SCHEMA_FACTORY_PREFIX)) {
        clear(props, k);
      }
    }
    return props;
  }

  private static void clear(final Properties props, final Object key) {
    if (props.remove(key) != null) {
      ModulizerLog.log("cleared property %s", key);
    }
  }

}
