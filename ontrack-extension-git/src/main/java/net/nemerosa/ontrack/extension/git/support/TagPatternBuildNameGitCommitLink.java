package net.nemerosa.ontrack.extension.git.support;

import net.nemerosa.ontrack.extension.git.model.BuildGitCommitLink;
import net.nemerosa.ontrack.model.structure.Build;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class TagPatternBuildNameGitCommitLink implements BuildGitCommitLink<TagPattern> {

    @Override
    public String getId() {
        return "tagPattern";
    }

    @Override
    public String getName() {
        return "Tag pattern";
    }

    @Override
    public TagPattern clone(TagPattern data, Function<String, String> replacementFunction) {
        return data.clone(replacementFunction);
    }

    @Override
    public String getCommitFromBuild(Build build, TagPattern data) {
        return data.getTagNameFromBuildName(build.getName())
                .orElseThrow(() -> new BuildTagPatternExcepton(data.getPattern(), build.getName()));
    }

}
