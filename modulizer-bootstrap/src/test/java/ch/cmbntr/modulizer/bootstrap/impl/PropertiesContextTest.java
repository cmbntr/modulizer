package ch.cmbntr.modulizer.bootstrap.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ch.cmbntr.modulizer.bootstrap.impl.PropertiesContext;

public class PropertiesContextTest {

  @Test
  public void testEmpty() {
    final PropertiesContext pc = PropertiesContext.empty();
    assertTrue(pc.isEmpty());
  }

  @Test
  public void testSystemProps() {
    final PropertiesContext pc = PropertiesContext.empty().addSystemProperties();
    final String key = "user.home";
    assertEquals(System.getProperty(key), pc.get(key));
  }

}
