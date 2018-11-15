package net.nemerosa.ontrack.git


import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

/**
 * Integration test for searching tags that contain a commit in a Git repository.
 *
 * The integration test creates the following Git history for the tests:
 *
 * <pre>
 * * e7c13f8 (HEAD, master) Commit 13
 * * e7c13f8 Commit 12
 * * e7c13f8 Commit 11
 * * e7c13f8 (tag: 1.0) Commit 10
 * * d7bd568 Commit 9
 * * a01269a (tag: 1.0-rc) Commit 8
 * * 92cd6e2 Commit 7
 * * 7e8bea2 Commit 6
 * | * fca8d9a (tag: 1.0-beta-1, 1.0) Commit 5
 * |/
 * * 95ccffb Commit 4
 * * ff4ff7c Commit 3
 * * 7e1f724 Commit 2
 * * 9c9bd64 Commit 1
 * </pre>
 *
 * (the commit SHA are for illustration purpose only, they cannot be relied upon)
 */
class GitGetTagsWhichContainCommitTest {

    private static GitRepo repo

    /**
     * Preparation of the Git repository
     */
    @BeforeClass
    static void 'Git repository'() {
        // Gets a repository
        repo = new GitRepo()
        println "Git repo at $repo"

        repo.with {

            // Initialises a Git repository
            git 'init'

            // Commits 1..4
            (1..4).each {
                commit(it)
            }

            // 1.0 branch and tag
            git 'checkout', '-b', '1.0'
            commit(5)
            git 'tag', '1.0-beta-1'

            // Going further with the master
            git 'checkout', 'master'

            // Commits and tags
            commit(6)
            commit(7)
            commit(8)
            git 'tag', '1.0-rc'
            commit(9)
            commit(10)
            git 'tag', '1.0'
            commit(11)
            commit(12)
            commit(13)

            // Log
            git 'log', '--oneline', '--graph', '--decorate', '--all'

        }
    }

    /**
     * Removing the Git repository
     */
    @AfterClass
    static void 'Git repository deletion'() {
        repo.close()
    }

    /**
     * When a tag is <i>on</i> the commit.
     *
     * <code>Commit 8</code> gives tag <code>1.1.0</code>.
     */
    @Test
    void 'tag_on_commit'() {
        // Identifying SHA for "Commit 8"
        def commit = repo.commitLookup('Commit 8')
        // Call
        def tags = repo.client.getTagsWhichContainCommit(commit)
        // Check (unordered)
        assert tags as Set == ['1.0-rc', '1.0'] as Set
    }

    /**
     * When a tag is between the HEAD and the commit.
     *
     * <code>Commit 6</code> gives tag <code>1.1.0</code>.
     */
    @Test
    void 'tag_on_path_to_head'() {
        // Identifying SHA for "Commit 6"
        def commit = repo.commitLookup('Commit 6')
        // Call
        def tags = repo.client.getTagsWhichContainCommit(commit)
        // Check (unordered)
        assert tags as Set == ['1.0-rc', '1.0'] as Set
    }

    /**
     * When a tag is <i>not</i> between the HEAD and the commit.
     *
     * <code>Commit 3</code> gives tag <code>1.0.1</code>.
     */
    @Test
    void 'tag_on_separate_path'() {
        // Identifying SHA for "Commit 3"
        def commit = repo.commitLookup('Commit 3')
        // Call
        def tags = repo.client.getTagsWhichContainCommit(commit)
        // Check (unordered)
        assert tags as Set == ['1.0-beta-1', '1.0-rc', '1.0'] as Set
    }

    /**
     * When no tag is found.
     *
     * <code>Commit 11</code> gives no tag.
     */
    @Test
    void 'no_tag'() {
        // Identifying SHA for "Commit 11"
        def commit = repo.commitLookup('Commit 11')
        // Call
        def tags = repo.client.getTagsWhichContainCommit(commit)
        // Check
        assert tags == []
    }

}
