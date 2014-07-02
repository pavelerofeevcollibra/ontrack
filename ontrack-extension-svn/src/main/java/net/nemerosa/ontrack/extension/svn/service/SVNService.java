package net.nemerosa.ontrack.extension.svn.service;

import net.nemerosa.ontrack.extension.svn.db.SVNIssueInfo;
import net.nemerosa.ontrack.extension.svn.db.SVNRepository;
import net.nemerosa.ontrack.extension.svn.model.SVNRepositoryIssue;
import net.nemerosa.ontrack.extension.svn.model.SVNRevisionInfo;
import net.nemerosa.ontrack.extension.svn.model.SVNRevisionPaths;

import java.util.List;
import java.util.Optional;

/**
 * Layer on top of the basic Subversion client and of the repositories.
 */
public interface SVNService {

    /**
     * Gets the information about a revision
     *
     * @param revision Revision to get information about
     * @return Full details about this revision
     */
    SVNRevisionInfo getRevisionInfo(SVNRepository repository, long revision);

    /**
     * Gets the list of changes for a revision
     */
    SVNRevisionPaths getRevisionPaths(SVNRepository repository, long revision);

    /**
     * Gets the list of revisions for an issue in a repository
     *
     * @param repository Repository to get the info from
     * @param key        Issue key
     * @return List of revisions associated with this issue
     */
    List<Long> getRevisionsForIssueKey(SVNRepository repository, String key);

    /**
     * Loads a {@link net.nemerosa.ontrack.extension.svn.db.SVNRepository} using its
     * configuration name.
     *
     * @param name Name of the {@link net.nemerosa.ontrack.extension.svn.model.SVNConfiguration}
     * @return Repository
     */
    SVNRepository getRepository(String name);

    /**
     * Searches a list of issues associated to this repository
     */
    Optional<SVNRepositoryIssue> searchIssues(SVNRepository repository, String token);

    /**
     * Gets information about an issue in a repository.
     *
     * @param configurationName Name of the repository configuration.
     * @param issueKey          Key of the issue
     * @return Information (never null, but can be empty)
     */
    SVNIssueInfo getIssueInfo(String configurationName, String issueKey);
}
