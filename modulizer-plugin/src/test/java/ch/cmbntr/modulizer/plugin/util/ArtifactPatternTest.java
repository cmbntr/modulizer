package ch.cmbntr.modulizer.plugin.util;

import static ch.cmbntr.modulizer.plugin.util.ArtifactInfo.createFilter;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.UUID;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.junit.Test;

import ch.cmbntr.modulizer.plugin.util.ArtifactInfo;
import ch.cmbntr.modulizer.plugin.util.ArtifactPattern;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class ArtifactPatternTest {

  private static final DefaultArtifactHandler ARTIFACT_HANDLER = new DefaultArtifactHandler();
  private static final String P1 = "g";
  private static final String P2 = "g:a";
  private static final String P3 = "g:a:c";
  private static final String P4 = "g:a:t:c";
  private static final String P5 = "g:a:t:c:s";

  private static final String W = "*";

  @Test
  public void testParsing() {

    final List<ArtifactPattern> pats = ArtifactPattern.createPatterns(ImmutableList.of(P1, P2, P3, P4, P5));

    assertPattern(pats.get(0), "g", W, W, W, W);
    assertPattern(pats.get(1), "g", "a", W, W, W);
    assertPattern(pats.get(2), "g", "a", W, "c", W);
    assertPattern(pats.get(3), "g", "a", "t", "c", W);
    assertPattern(pats.get(4), "g", "a", "t", "c", "s");
  }

  @Test
  public void testMatching() {

    final List<ArtifactPattern> pats = ArtifactPattern.createPatterns(ImmutableList.of(P1, P2, P3, P4, P5));
    assertMatch(pats.get(0), "g", null, null, null, null);
    assertMatch(pats.get(0), "g", rnd(), rnd(), rnd(), rnd());

    assertMatch(pats.get(1), "g", "a", null, null, null);
    assertMatch(pats.get(1), "g", "a", rnd(), rnd(), rnd());

    assertMatch(pats.get(2), "g", "a", null, "c", null);
    assertMatch(pats.get(2), "g", "a", rnd(), "c", rnd());

    assertMatch(pats.get(3), "g", "a", "t", "c", null);
    assertMatch(pats.get(3), "g", "a", "t", "c", rnd());

    assertMatch(pats.get(4), "g", "a", "t", "c", "s");
  }

  @Test
  public void testMatching2() {
    final Artifact art = dummy("ch.cmbntr", "modulizer-bootstrap", "jar", null, "compile");
    final ArtifactPattern pat = ArtifactPattern.valueOf("*:modulizer-bootstrap:jar:*:*");
    assertEquals(art, Iterables.find(ImmutableList.of(art), createFilter(pat)));
  }

  private String rnd() {
    return UUID.randomUUID().toString();
  }

  private String valOrRnd(final String v) {
    return v == null ? rnd() : v;
  }

  private void assertMatch(final ArtifactPattern pat, final String g, final String a, final String t, final String c,
      final String s) {
    assertTrue(pat.apply(ArtifactInfo.from(dummy(g, a, t, c, s))));
  }

  private Artifact dummy(final String g, final String a, final String t, final String c, final String s) {
    final String v = UUID.randomUUID().toString();
    return new DefaultArtifact(valOrRnd(g), valOrRnd(a), valOrRnd(v), valOrRnd(s), valOrRnd(t), c, ARTIFACT_HANDLER);
  }

  private void assertPattern(final ArtifactPattern pat, final String expectedG, final String expectedA,
      final String expectedT, final String expectedC, final String expectedS) {
    assertEquals(expectedG, pat.groupId);
    assertEquals(expectedA, pat.artifactId);
    assertEquals(expectedT, pat.type);
    assertEquals(expectedC, pat.classifier);
    assertEquals(expectedS, pat.scope);
  }

}
