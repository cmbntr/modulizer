package ch.cmbntr.modulizer.plugin.util;

import java.util.List;
import java.util.Set;

import org.codehaus.plexus.util.SelectorUtils;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class ArtifactPattern extends ArtifactInfo implements Predicate<ArtifactInfo> {

  private static final Function<String, ArtifactPattern> CREATE = new Function<String, ArtifactPattern>() {
    @Override
    public ArtifactPattern apply(final String pattern) {
      return ArtifactPattern.valueOf(pattern);
    }
  };

  protected ArtifactPattern(final String groupId, final String artifactId, final String type, final String classifier,
      final String scope) {
    super(groupId, artifactId, type, classifier, scope);
  }

  public static ArtifactPattern valueOf(final String pattern) {
    String[] tokens = new String[0];
    if (pattern != null && pattern.length() > 0) {
      tokens = pattern.split(":", -1);
    }
    final String g = tokens.length > 0 ? tokens[0] : "";
    final String a = tokens.length > 1 ? tokens[1] : "*";
    final String t = tokens.length > 3 ? tokens[2] : "*";
    final String c = tokens.length > 3 ? tokens[3] : tokens.length > 2 ? tokens[2] : "*";
    final String s = tokens.length > 4 ? tokens[4] : "*";
    return new ArtifactPattern(g, a, t, c, s);
  }

  public static List<ArtifactPattern> createPatterns(final Iterable<String> patterns) {
    final Set<String> p = patterns == null ? ImmutableSet.<String> of() : ImmutableSet.copyOf(patterns);
    return ImmutableList.copyOf(Collections2.transform(p, CREATE));
  }

  @Override
  public boolean apply(final ArtifactInfo a) {
    if (matchesNot(this.scope, a.scope)) {
      return false;
    }
    if (matchesNot(this.groupId, a.groupId)) {
      return false;
    }
    if (matchesNot(this.artifactId, a.artifactId)) {
      return false;
    }
    if (matchesNot(this.type, a.type)) {
      return false;
    }
    if (matchesNot(this.classifier, a.classifier)) {
      return false;
    }

    return true;
  }

  private boolean matchesNot(final String pattern, final String s) {
    return !SelectorUtils.match(pattern, s);
  }

}