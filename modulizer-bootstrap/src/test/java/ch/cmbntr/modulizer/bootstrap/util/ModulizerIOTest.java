package ch.cmbntr.modulizer.bootstrap.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import ch.cmbntr.modulizer.bootstrap.util.ModulizerIO;

public class ModulizerIOTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void testSHA1() throws Exception {

    final File empty1 = tmpFile("da39a3ee5e6b4b0d3255bfef95601890afd80709.txt", 0);
    final File empty2 = tmpFile(null, 0);
    final File someZeros = tmpFile(null, 8000);

    assertTrue(ModulizerIO.verifySHA1Named(empty1));
    assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709.tmp", ModulizerIO.sha1Name(empty2));
    assertEquals("75a289eec0a33499fbaa6e89081c0efa604fbdd4.tmp", ModulizerIO.sha1Name(someZeros));
  }

  private File tmpFile(final String name, final int numZeros) throws IOException, FileNotFoundException {
    final File f = name == null ? this.tmp.newFile() : this.tmp.newFile(name);
    if (numZeros > 0) {
      final FileOutputStream fos = new FileOutputStream(f);
      try {
        fos.write(new byte[numZeros]);
      } finally {
        fos.close();
      }
    }
    assertEquals(numZeros, f.length());
    return f;
  }

}
