package ch.cmbntr.modulizer.plugin.util;

import static com.google.common.base.Predicates.and;
import static com.google.common.base.Predicates.compose;

import org.apache.maven.artifact.Artifact;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;

public class ArtifactInfo {

  private static final Function<Artifact, ArtifactInfo> CREATE = new Function<Artifact, ArtifactInfo>() {
    @Override
    public ArtifactInfo apply(final Artifact a) {
      return ArtifactInfo.from(a);
    }
  };

  final String groupId;

  final String artifactId;

  final String type;

  final String classifier;

  final String scope;

  protected ArtifactInfo(final String groupId, final String artifactId, final String type, final String classifier,
      final String scope) {
    this.groupId = groupId != null ? groupId : "";
    this.artifactId = artifactId != null ? artifactId : "";
    this.type = type != null ? type : "";
    this.classifier = classifier != null ? classifier : "";
    this.scope = scope != null ? scope : "";
  }

  public static ArtifactInfo from(final Artifact artifact) {
    return new ArtifactInfo(artifact.getGroupId(), artifact.getArtifactId(), artifact.getType(),
        artifact.getClassifier(), artifact.getScope());
  }

  public static Predicate<Artifact> createFilter(final Predicate<ArtifactInfo>... criteria) {
    return compose(and(criteria), CREATE);
  }

  public static Predicate<Artifact> createFilter(final Iterable<? extends Predicate<ArtifactInfo>> criteria) {
    return compose(and(criteria), CREATE);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this).add("groupId", this.groupId).add("artifactId", this.artifactId)
        .add("type", this.type).add("classifier", this.classifier).add("scope", this.scope).toString();
  }

}