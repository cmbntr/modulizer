package ch.cmbntr.modulizer.bootstrap.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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

  @Test
  public void testInterpolate() {
    final PropertiesContext pc = PropertiesContext.empty().addSystemProperties();
    pc.put("foo", "42");

    pc.put("test1", "the answer is ${foo}");
    assertEquals("the answer is 42", pc.getInterpolated("test1"));

    pc.put("test2", "${unknown} ${foo} ${unknown} ${foo} ${java.home}");
    assertEquals("${unknown} 42 ${unknown} 42 " + pc.get("java.home"), pc.getInterpolated("test2"));
  }

  @Test
  public void testInterpolateRecursionTrap() {
    final PropertiesContext pc = PropertiesContext.empty().addSystemProperties();
    pc.put("flip", "${flop}");
    pc.put("flop", "${flip}");

    pc.put("flipper1", "${flip} ${flop} ${unknown} ${flop} ${flip}");
    assertEquals("${flip} ${flip} ${unknown} ${flip} ${flip}", pc.getInterpolated("flipper1"));

    pc.put("flipper2", "${flop} ${flip} ${unknown} ${flip} ${flop}");
    assertEquals("${flop} ${flop} ${unknown} ${flop} ${flop}", pc.getInterpolated("flipper2"));
  }

}
