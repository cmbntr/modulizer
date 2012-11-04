package ch.cmbntr.modulizer.plugin.config;

import static com.google.common.base.Predicates.not;
import static com.google.common.base.Predicates.or;

import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;

import ch.cmbntr.modulizer.plugin.util.ArtifactInfo;
import ch.cmbntr.modulizer.plugin.util.ArtifactPattern;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;

public class ModuleResources extends ConfigBase {

  private static final Predicate<ArtifactInfo> IS_NO_TEST = not(ArtifactPattern.valueOf("*:*:*:*:test"));
  private static final Predicate<ArtifactInfo> IS_NO_POM = not(ArtifactPattern.valueOf("*:*:pom:*:*"));

  private boolean excludeTest = true;

  private Set<String> includes;

  private Set<String> excludes;

  private Predicate<ArtifactInfo> pred(final Set<String> patterns, final Predicate<ArtifactInfo> defPredicate) {
    final List<? extends Predicate<ArtifactInfo>> preds = ArtifactPattern.createPatterns(patterns);
    if (preds.isEmpty()) {
      return defPredicate;
    } else {
      return or(preds);
    }
  }

  public Predicate<Artifact> getArtifactSelector() {
    final List<Predicate<ArtifactInfo>> criteria = Lists.newLinkedList();
    criteria.add(IS_NO_POM);
    if (this.excludeTest) {
      criteria.add(IS_NO_TEST);
    }
    criteria.add(pred(this.includes, Predicates.<ArtifactInfo> alwaysTrue()));
    criteria.add(not(pred(this.excludes, Predicates.<ArtifactInfo> alwaysFalse())));

    return ArtifactInfo.createFilter(criteria);
  }

  @Override
  protected ToStringHelper prepareToStringHelper() {
    return super.prepareToStringHelper().add("excludeTest", this.excludeTest).add("includes", this.includes)
        .add("excludes", this.excludes);
  }

}
