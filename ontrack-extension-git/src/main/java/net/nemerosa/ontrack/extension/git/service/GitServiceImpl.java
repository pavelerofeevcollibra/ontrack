package net.nemerosa.ontrack.extension.git.service;

import com.google.common.collect.Lists;
import net.nemerosa.ontrack.extension.api.model.BuildDiffRequest;
import net.nemerosa.ontrack.extension.git.client.GitDiff;
import net.nemerosa.ontrack.extension.git.client.GitDiffEntry;
import net.nemerosa.ontrack.extension.git.client.GitTag;
import net.nemerosa.ontrack.extension.git.model.*;
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationProperty;
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationPropertyType;
import net.nemerosa.ontrack.extension.git.support.TagBuildNameGitCommitLink;
import net.nemerosa.ontrack.extension.issues.IssueServiceRegistry;
import net.nemerosa.ontrack.extension.issues.model.ConfiguredIssueService;
import net.nemerosa.ontrack.extension.issues.model.Issue;
import net.nemerosa.ontrack.extension.issues.model.IssueServiceConfigurationRepresentation;
import net.nemerosa.ontrack.extension.issues.model.IssueServiceNotConfiguredException;
import net.nemerosa.ontrack.extension.scm.model.SCMBuildView;
import net.nemerosa.ontrack.extension.scm.model.SCMChangeLogFileChangeType;
import net.nemerosa.ontrack.extension.scm.service.AbstractSCMChangeLogService;
import net.nemerosa.ontrack.git.GitRepositoryClient;
import net.nemerosa.ontrack.git.GitRepositoryClientFactory;
import net.nemerosa.ontrack.git.exceptions.GitRepositorySyncException;
import net.nemerosa.ontrack.git.model.GitCommit;
import net.nemerosa.ontrack.git.model.GitLog;
import net.nemerosa.ontrack.model.Ack;
import net.nemerosa.ontrack.model.job.*;
import net.nemerosa.ontrack.model.security.SecurityService;
import net.nemerosa.ontrack.model.structure.*;
import net.nemerosa.ontrack.model.support.ApplicationLogService;
import net.nemerosa.ontrack.model.support.MessageAnnotationUtils;
import net.nemerosa.ontrack.model.support.MessageAnnotator;
import net.nemerosa.ontrack.tx.Transaction;
import net.nemerosa.ontrack.tx.TransactionService;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Service
public class GitServiceImpl extends AbstractSCMChangeLogService<FormerGitConfiguration, GitBuildInfo, GitChangeLogIssue> implements GitService, JobProvider {

    private final Logger logger = LoggerFactory.getLogger(GitService.class);
    private final PropertyService propertyService;
    private final IssueServiceRegistry issueServiceRegistry;
    private final JobQueueService jobQueueService;
    private final SecurityService securityService;
    private final TransactionService transactionService;
    private final ApplicationLogService applicationLogService;
    private final GitRepositoryClientFactory gitRepositoryClientFactory;
    private final BuildGitCommitLinkService buildGitCommitLinkService;
    private final GitConfigurationService gitConfigurationService;
    private final Collection<GitConfigurator> gitConfigurators;

    @Autowired
    public GitServiceImpl(
            StructureService structureService,
            PropertyService propertyService,
            IssueServiceRegistry issueServiceRegistry,
            JobQueueService jobQueueService,
            SecurityService securityService,
            TransactionService transactionService,
            ApplicationLogService applicationLogService,
            GitRepositoryClientFactory gitRepositoryClientFactory,
            BuildGitCommitLinkService buildGitCommitLinkService,
            GitConfigurationService gitConfigurationService,
            Collection<GitConfigurator> gitConfigurators) {
        super(structureService, propertyService);
        this.propertyService = propertyService;
        this.issueServiceRegistry = issueServiceRegistry;
        this.jobQueueService = jobQueueService;
        this.securityService = securityService;
        this.transactionService = transactionService;
        this.applicationLogService = applicationLogService;
        this.gitRepositoryClientFactory = gitRepositoryClientFactory;
        this.buildGitCommitLinkService = buildGitCommitLinkService;
        this.gitConfigurationService = gitConfigurationService;
        this.gitConfigurators = gitConfigurators;
    }

    @Override
    public void forEachConfiguredProject(BiConsumer<Project, GitConfiguration> consumer) {
        structureService.getProjectList().stream()
                .forEach(project -> {
                    Optional<GitConfiguration> configuration = getProjectConfiguration(project);
                    if (configuration.isPresent()) {
                        consumer.accept(project, configuration.get());
                    }
                });
    }

    @Override
    public void forEachConfiguredBranch(BiConsumer<Branch, GitBranchConfiguration> consumer) {
        for (Project project : structureService.getProjectList()) {
            structureService.getBranchesForProject(project.getId()).stream()
                    .filter(branch -> branch.getType() != BranchType.TEMPLATE_DEFINITION)
                    .forEach(branch -> {
                        Optional<GitBranchConfiguration> configuration = getBranchConfiguration(branch);
                        if (configuration.isPresent()) {
                            consumer.accept(branch, configuration.get());
                        }
                    });
        }
    }

    @Override
    public Collection<Job> getJobs() {
        Collection<Job> jobs = new ArrayList<>();
        // Indexation of repositories, based on projects actually linked
        forEachConfiguredProject((project, configuration) -> {
            jobs.add(createIndexationJob(configuration));
        });
        // Synchronisation of branch builds with tags when applicable
        forEachConfiguredBranch((branch, branchConfiguration) -> {
            // Build/tag sync job
            if (branchConfiguration.getBuildTagInterval() > 0
                    && branchConfiguration.getBuildCommitLink().getLink() instanceof IndexableBuildGitCommitLink) {
                jobs.add(createBuildSyncJob(branch, branchConfiguration));
            }
        });
        return jobs;
    }

    @Override
    public boolean isBranchConfiguredForGit(Branch branch) {
        return getBranchConfiguration(branch).isPresent();
    }

    @Override
    public Ack launchBuildSync(ID branchId) {
        // Gets the branch
        Branch branch = structureService.getBranch(branchId);
        // Gets its configuration
        Optional<GitBranchConfiguration> branchConfiguration = getBranchConfiguration(branch);
        // If valid, launches a job
        if (branchConfiguration.isPresent() && branchConfiguration.get().getBuildCommitLink().getLink() instanceof IndexableBuildGitCommitLink) {
            return jobQueueService.queue(createBuildSyncJob(branch, branchConfiguration.get()));
        }
        // Else, nothing has happened
        else {
            return Ack.NOK;
        }
    }

    @Override
    @Transactional
    public GitChangeLog changeLog(BuildDiffRequest request) {
        try (Transaction ignored = transactionService.start()) {
            Branch branch = structureService.getBranch(request.getBranch());
            Project project = branch.getProject();
            GitRepositoryClient client = getGitRepositoryClient(project);
            // Forces Git sync before
            boolean syncError;
            try {
                client.sync(logger::debug);
                syncError = false;
            } catch (GitRepositorySyncException ex) {
                applicationLogService.error(
                        ex,
                        GitService.class,
                        branch.getId().toString(),
                        String.format(
                                "Change log for %s",
                                branch.getName()
                        ),
                        String.format(
                                "%s (%s -> %s)",
                                branch.getName(),
                                request.getFrom(),
                                request.getTo()
                        )
                );
                syncError = true;
            }
            // Change log computation
            return new GitChangeLog(
                    UUID.randomUUID().toString(),
                    branch.getProject(),
                    branch,
                    getSCMBuildView(request.getFrom()),
                    getSCMBuildView(request.getTo()),
                    syncError
            );
        }
    }

    protected GitConfiguration getRequiredProjectConfiguration(Project project) {
        return getProjectConfiguration(project)
                .orElseThrow(() -> new GitProjectNotConfiguredException(project.getId()));
    }

    protected GitRepositoryClient getGitRepositoryClient(Project project) {
        return getProjectConfiguration(project)
                .map(GitConfiguration::getGitRepository)
                .map(gitRepositoryClientFactory::getClient)
                .orElseThrow(() -> new GitProjectNotConfiguredException(project.getId()));
    }

    @Override
    public GitChangeLogCommits getChangeLogCommits(GitChangeLog changeLog) {
        // Gets the client
        GitRepositoryClient client = getGitRepositoryClient(changeLog.getProject());
        // Gets the build boundaries
        Build buildFrom = changeLog.getFrom().getBuild();
        Build buildTo = changeLog.getTo().getBuild();
        // Commit boundaries
        String commitFrom = getCommitFromBuild(buildFrom);
        String commitTo = getCommitFromBuild(buildTo);
        // Gets the commits
        GitLog log = client.graph(commitFrom, commitTo);
        // If log empty, inverts the boundaries
        if (log.getCommits().isEmpty()) {
            String t = commitFrom;
            commitFrom = commitTo;
            commitTo = t;
            log = client.graph(commitFrom, commitTo);
        }
        // Consolidation to UI
        List<GitCommit> commits = log.getCommits();
        List<GitUICommit> uiCommits = toUICommits(getRequiredProjectConfiguration(changeLog.getProject()), commits);
        return new GitChangeLogCommits(
                new GitUILog(
                        log.getPlot(),
                        uiCommits
                )
        );
    }

    protected String getCommitFromBuild(Build build) {
        return getBranchConfiguration(build.getBranch())
                .map(c -> c.getBuildCommitLink().getCommitFromBuild(build))
                .orElseThrow(() -> new GitBranchNotConfiguredException(build.getBranch().getId()));
    }

    @Override
    public GitChangeLogIssues getChangeLogIssues(GitChangeLog changeLog) {
        // Commits must have been loaded first
        if (changeLog.getCommits() == null) {
            changeLog.withCommits(getChangeLogCommits(changeLog));
        }
        // In a transaction
        try (Transaction ignored = transactionService.start()) {
            // Configuration
            GitConfiguration configuration = getRequiredProjectConfiguration(changeLog.getProject());
            // Issue service
            ConfiguredIssueService configuredIssueService = issueServiceRegistry.getConfiguredIssueService(configuration.getIssueServiceConfigurationIdentifier());
            if (configuredIssueService == null) {
                throw new IssueServiceNotConfiguredException();
            }
            // Index of issues, sorted by keys
            Map<String, GitChangeLogIssue> issues = new TreeMap<>();
            // For all commits in this commit log
            for (GitUICommit gitUICommit : changeLog.getCommits().getLog().getCommits()) {
                Set<String> keys = configuredIssueService.extractIssueKeysFromMessage(gitUICommit.getCommit().getFullMessage());
                for (String key : keys) {
                    GitChangeLogIssue existingIssue = issues.get(key);
                    if (existingIssue != null) {
                        existingIssue.add(gitUICommit);
                    } else {
                        Issue issue = configuredIssueService.getIssue(key);
                        existingIssue = GitChangeLogIssue.of(issue, gitUICommit);
                        issues.put(key, existingIssue);
                    }
                }
            }
            // List of issues
            List<GitChangeLogIssue> issuesList = new ArrayList<>(issues.values());
            // Issues link
            IssueServiceConfigurationRepresentation issueServiceConfiguration = configuredIssueService.getIssueServiceConfigurationRepresentation();
            // OK
            return new GitChangeLogIssues(issueServiceConfiguration, issuesList);

        }
    }

    @Override
    public GitChangeLogFiles getChangeLogFiles(GitChangeLog changeLog) {
        // Gets the configuration
        GitConfiguration configuration = getRequiredProjectConfiguration(changeLog.getProject());
        // Gets the client for this project
        GitRepositoryClient client = gitRepositoryClientFactory.getClient(configuration.getGitRepository());
        // Gets the build boundaries
        Build buildFrom = changeLog.getFrom().getBuild();
        Build buildTo = changeLog.getTo().getBuild();
        // Commit boundaries
        String commitFrom = getCommitFromBuild(buildFrom);
        String commitTo = getCommitFromBuild(buildTo);
        // FIXME Diff
        // final GitDiff diff = client.diff(commitFrom, commitTo);
        // FIXME File change links
        // String fileChangeLinkFormat = gitConfiguration.getFileAtCommitLink();
        // OK
//        return new GitChangeLogFiles(
//                diff.getEntries().stream()
//                        .map(entry -> toChangeLogFile(entry).withUrl(
//                                getDiffUrl(diff, entry, fileChangeLinkFormat)
//                        ))
//                        .collect(Collectors.toList())
//        );
        throw new RuntimeException("NYI");
    }

    @Override
    public boolean scanCommits(GitConfiguration configuration, Predicate<RevCommit> scanFunction) {
        // Gets the client
        GitRepositoryClient client = gitRepositoryClientFactory.getClient(configuration.getGitRepository());
        // FIXME Scanning
//        return client.scanCommits(scanFunction);
        throw new RuntimeException("NYI");
    }

    @Override
    public OntrackGitIssueInfo getIssueInfo(ID branchId, String key) {
        Branch branch = structureService.getBranch(branchId);
        // Configuration
        GitBranchConfiguration branchConfiguration = getRequiredBranchConfiguration(branch);
        GitConfiguration configuration = branchConfiguration.getConfiguration();
        // Issue service
        ConfiguredIssueService configuredIssueService = issueServiceRegistry.getConfiguredIssueService(configuration.getIssueServiceConfigurationIdentifier());
        if (configuredIssueService == null) {
            throw new GitBranchIssueServiceNotConfiguredException(branchId);
        }
        // Gets the details about the issue
        Issue issue = configuredIssueService.getIssue(key);

        // Collects commits per branches
        List<OntrackGitIssueCommitInfo> commitInfos = collectIssueCommitInfos(key);

        // OK
        return new OntrackGitIssueInfo(
                configuredIssueService.getIssueServiceConfigurationRepresentation(),
                issue,
                commitInfos
        );
    }

    private List<OntrackGitIssueCommitInfo> collectIssueCommitInfos(String key) {
        // Index of commit infos
        Map<String, OntrackGitIssueCommitInfo> commitInfos = new LinkedHashMap<>();
        // For all configured branches
        forEachConfiguredBranch((branch, branchConfiguration) -> {
            GitConfiguration configuration = branchConfiguration.getConfiguration();
            // Gets the Git client for this project
            GitRepositoryClient client = gitRepositoryClientFactory.getClient(configuration.getGitRepository());
            // Issue service
            ConfiguredIssueService configuredIssueService = issueServiceRegistry.getConfiguredIssueService(configuration.getIssueServiceConfigurationIdentifier());
            if (configuredIssueService != null) {
                // List of commits for this branch
                List<RevCommit> revCommits = new ArrayList<>();
                // FIXME Scanning this branch's repository for the commit
//                gitClient.scanCommits(revCommit -> {
//                    String message = revCommit.getFullMessage();
//                    Set<String> keys = configuredIssueService.extractIssueKeysFromMessage(message);
//                    if (configuredIssueService.containsIssueKey(key, keys)) {
//                        // We have a commit for this branch!
//                        revCommits.add(revCommit);
//                    }
//                    return false; // Scanning all commits
//                });
                // If at least one commit
                if (revCommits.size() > 0) {
                    // Gets the last commit (which is the first in the list)
                    RevCommit revCommit = revCommits.get(0);
                    // FIXME Commit explained (independent from the branch)
//                    GitCommit commit = gitClient.toCommit(revCommit);
//                    String commitId = commit.getId();
//                    // Gets any existing commit info
//                    OntrackGitIssueCommitInfo commitInfo = commitInfos.get(commitId);
//                    // If not defined, creates an entry
//                    if (commitInfo == null) {
//                        // UI commit (independent from the branch)
//                        GitUICommit uiCommit = toUICommit(
//                                configuration.getCommitLink(),
//                                getMessageAnnotators(configuration),
//                                commit
//                        );
//                        // Commit info
//                        commitInfo = OntrackGitIssueCommitInfo.of(uiCommit);
//                        // Indexation
//                        commitInfos.put(commitId, commitInfo);
//                    }
                    // Collects branch info
                    OntrackGitIssueCommitBranchInfo branchInfo = OntrackGitIssueCommitBranchInfo.of(branch);
                    // FIXME Gets the last build for this branch
//                    Optional<Build> buildAfterCommit = getEarliestBuildAfterCommit(commitId, branch, configuration, gitClient);
//                    if (buildAfterCommit.isPresent()) {
//                        Build build = buildAfterCommit.get();
//                        // Gets the build view
//                        BuildView buildView = structureService.getBuildView(build);
//                        // Adds it to the list
//                        branchInfo = branchInfo.withBuildView(buildView);
//                        // Collects the promotions for the branch
//                        branchInfo = branchInfo.withBranchStatusView(
//                                structureService.getEarliestPromotionsAfterBuild(build)
//                        );
//                    }
                    // FIXME Adds the info
//                    commitInfo.add(branchInfo);
                }
            }
        });
        // OK
        return Lists.newArrayList(commitInfos.values());
    }

    @Override
    public Optional<GitUICommit> lookupCommit(GitConfiguration configuration, String id) {
        // Gets the client client for this configuration
        GitRepositoryClient gitClient = gitRepositoryClientFactory.getClient(configuration.getGitRepository());
        // FIXME Gets the commit
//        GitCommit gitCommit = gitClient.getCommitFor(id);
//        if (gitCommit != null) {
//            String commitLink = configuration.getCommitLink();
//            List<? extends MessageAnnotator> messageAnnotators = getMessageAnnotators(configuration);
//            return Optional.of(
//                    toUICommit(
//                            commitLink,
//                            messageAnnotators,
//                            gitCommit
//                    )
//            );
//        } else {
        return Optional.empty();
//        }
    }

    @Override
    public OntrackGitCommitInfo getCommitInfo(ID branchId, String commit) {
        /**
         * The information is actually collected on all branches.
         */
        return getOntrackGitCommitInfo(commit);
    }

    @Override
    public List<String> getRemoteBranches(GitConfiguration configuration) {
        GitRepositoryClient gitClient = gitRepositoryClientFactory.getClient(configuration.getGitRepository());
        gitClient.sync(logger::debug);
        // FIXME return gitClient.getRemoteBranches();
        return Collections.emptyList();
    }

    private OntrackGitCommitInfo getOntrackGitCommitInfo(String commit) {
        // Reference data
        AtomicReference<GitCommit> theCommit = new AtomicReference<>();
        AtomicReference<GitConfiguration> theConfiguration = new AtomicReference<>();
        // Data to collect
        Collection<BuildView> buildViews = new ArrayList<>();
        Collection<BranchStatusView> branchStatusViews = new ArrayList<>();
        // For all configured branches
        forEachConfiguredBranch((branch, branchConfiguration) -> {
            GitConfiguration configuration = branchConfiguration.getConfiguration();
            // Gets the client client for this branch
            GitRepositoryClient gitClient = gitRepositoryClientFactory.getClient(configuration.getGitRepository());
            // FIXME Scan for this commit in this branch
            AtomicReference<RevCommit> revCommitRef = new AtomicReference<>();
//            gitClient.scanCommits(revCommit -> {
//                String commitId = gitClient.getId(revCommit);
//                if (StringUtils.equals(commit, commitId)) {
//                    revCommitRef.set(revCommit);
//                    return true;
//                } else {
//                    return false;
//                }
//            });
            // If present...
            RevCommit revCommit = revCommitRef.get();
            if (revCommit != null) {
                // Reference
                if (theCommit.get() == null) {
                    theCommit.set(gitClient.toCommit(revCommit));
                    theConfiguration.set(configuration);
                }
                // Gets the earliest build on this branch that contains this commit
                getEarliestBuildAfterCommit(commit, branch, branchConfiguration, gitClient)
                        // ... and it present collect its data
                        .ifPresent(build -> {
                            // Gets the build view
                            BuildView buildView = structureService.getBuildView(build);
                            // Adds it to the list
                            buildViews.add(buildView);
                            // Collects the promotions for the branch
                            branchStatusViews.add(
                                    structureService.getEarliestPromotionsAfterBuild(build)
                            );
                        });
            }
        });

        // OK
        if (theCommit.get() != null) {
            String commitLink = theConfiguration.get().getCommitLink();
            List<? extends MessageAnnotator> messageAnnotators = getMessageAnnotators(theConfiguration.get());
            return new OntrackGitCommitInfo(
                    toUICommit(
                            commitLink,
                            messageAnnotators,
                            theCommit.get()
                    ),
                    buildViews,
                    branchStatusViews
            );
        } else {
            throw new GitCommitNotFoundException(commit);
        }

    }

    protected <T> Optional<Build> getEarliestBuildAfterCommit(String commit, Branch branch, GitBranchConfiguration branchConfiguration, GitRepositoryClient gitClient) {
        @SuppressWarnings("unchecked")
        ConfiguredBuildGitCommitLink<T> configuredBuildGitCommitLink = (ConfiguredBuildGitCommitLink<T>) branchConfiguration.getBuildCommitLink();
        // FIXME Delegates to the build commit link...
//        return configuredBuildGitCommitLink.getLink()
//                // ... by getting candidate references
//                .getBuildCandidateReferences(commit, gitClient, configuredBuildGitCommitLink.getData())
//                        // ... gets the builds
//                .map(buildName -> structureService.findBuildByName(branch.getProject().getName(), branch.getName(), buildName))
//                        // ... filter on existing builds
//                .filter(Optional::isPresent).map(Optional::get)
//                        // ... filter the builds using the link
//                .filter(build -> configuredBuildGitCommitLink.getLink().isBuildEligible(build, configuredBuildGitCommitLink.getData()))
//                        // ... sort by decreasing date
//                .sorted((o1, o2) -> (o1.id() - o2.id()))
//                        // ... takes the first build
//                .findFirst();
        throw new RuntimeException("NYI");
    }

    private String getDiffUrl(GitDiff diff, GitDiffEntry entry, String fileChangeLinkFormat) {
        return fileChangeLinkFormat
                .replace("{commit}", entry.getReferenceId(diff.getFrom(), diff.getTo()))
                .replace("{path}", entry.getReferencePath());
    }

    private GitChangeLogFile toChangeLogFile(GitDiffEntry entry) {
        switch (entry.getChangeType()) {
            case ADD:
                return GitChangeLogFile.of(SCMChangeLogFileChangeType.ADDED, entry.getNewPath());
            case COPY:
                return GitChangeLogFile.of(SCMChangeLogFileChangeType.COPIED, entry.getOldPath(), entry.getNewPath());
            case DELETE:
                return GitChangeLogFile.of(SCMChangeLogFileChangeType.DELETED, entry.getOldPath());
            case MODIFY:
                return GitChangeLogFile.of(SCMChangeLogFileChangeType.MODIFIED, entry.getOldPath());
            case RENAME:
                return GitChangeLogFile.of(SCMChangeLogFileChangeType.RENAMED, entry.getOldPath(), entry.getNewPath());
            default:
                return GitChangeLogFile.of(SCMChangeLogFileChangeType.UNDEFINED, entry.getOldPath(), entry.getNewPath());
        }
    }

    private List<GitUICommit> toUICommits(GitConfiguration gitConfiguration, List<GitCommit> commits) {
        // Link?
        String commitLink = gitConfiguration.getCommitLink();
        // Issue-based annotations
        List<? extends MessageAnnotator> messageAnnotators = getMessageAnnotators(gitConfiguration);
        // OK
        return commits.stream()
                .map(commit -> toUICommit(commitLink, messageAnnotators, commit))
                .collect(Collectors.toList());
    }

    private GitUICommit toUICommit(String commitLink, List<? extends MessageAnnotator> messageAnnotators, GitCommit commit) {
        return new GitUICommit(
                commit,
                MessageAnnotationUtils.annotate(commit.getShortMessage(), messageAnnotators),
                MessageAnnotationUtils.annotate(commit.getFullMessage(), messageAnnotators),
                StringUtils.replace(commitLink, "{commit}", commit.getId())
        );
    }

    private List<? extends MessageAnnotator> getMessageAnnotators(GitConfiguration gitConfiguration) {
        List<? extends MessageAnnotator> messageAnnotators;
        String issueServiceConfigurationIdentifier = gitConfiguration.getIssueServiceConfigurationIdentifier();
        if (StringUtils.isNotBlank(issueServiceConfigurationIdentifier)) {
            ConfiguredIssueService configuredIssueService = issueServiceRegistry.getConfiguredIssueService(issueServiceConfigurationIdentifier);
            if (configuredIssueService != null) {
                // Gets the message annotator
                Optional<MessageAnnotator> messageAnnotator = configuredIssueService.getMessageAnnotator();
                // If present annotate the messages
                if (messageAnnotator.isPresent()) {
                    messageAnnotators = Collections.singletonList(messageAnnotator.get());
                } else {
                    messageAnnotators = Collections.emptyList();
                }
            } else {
                messageAnnotators = Collections.emptyList();
            }
        } else {
            messageAnnotators = Collections.emptyList();
        }
        return messageAnnotators;
    }

    private SCMBuildView<GitBuildInfo> getSCMBuildView(ID buildId) {
        return new SCMBuildView<>(getBuildView(buildId), new GitBuildInfo());
    }

    @Override
    public Optional<GitConfiguration> getProjectConfiguration(Project project) {
        return gitConfigurators.stream()
                .map(c -> c.getConfiguration(project))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    protected GitBranchConfiguration getRequiredBranchConfiguration(Branch branch) {
        return getBranchConfiguration(branch)
                .orElseThrow(() -> new GitBranchNotConfiguredException(branch.getId()));
    }

    @Override
    public Optional<GitBranchConfiguration> getBranchConfiguration(Branch branch) {
        // Get the configuration for the project
        Optional<GitConfiguration> configuration = getProjectConfiguration(branch.getProject());
        if (configuration.isPresent()) {
            // Gets the configuration for a branch
            String gitBranch;
            ConfiguredBuildGitCommitLink<?> buildCommitLink;
            boolean override;
            int buildTagInterval;
            Property<GitBranchConfigurationProperty> branchConfig = propertyService.getProperty(branch, GitBranchConfigurationPropertyType.class);
            if (!branchConfig.isEmpty()) {
                gitBranch = branchConfig.getValue().getBranch();
                buildCommitLink = toConfiguredBuildGitCommitLink(
                        branchConfig.getValue().getBuildCommitLink()
                );
                override = branchConfig.getValue().isOverride();
                buildTagInterval = branchConfig.getValue().getBuildTagInterval();
            } else {
                gitBranch = "master";
                buildCommitLink = TagBuildNameGitCommitLink.DEFAULT;
                override = false;
                buildTagInterval = 0;
            }
            // OK
            return Optional.of(
                    new GitBranchConfiguration(
                            configuration.get(),
                            gitBranch,
                            buildCommitLink,
                            override,
                            buildTagInterval
                    )
            );
        } else {
            return Optional.empty();
        }
    }

    private <T> ConfiguredBuildGitCommitLink<T> toConfiguredBuildGitCommitLink(ServiceConfiguration serviceConfiguration) {
        @SuppressWarnings("unchecked")
        BuildGitCommitLink<T> link = (BuildGitCommitLink<T>) buildGitCommitLinkService.getLink(serviceConfiguration.getId());
        T linkData = link.parseData(serviceConfiguration.getData());
        return new ConfiguredBuildGitCommitLink<>(
                link,
                linkData
        );
    }

    private Job createBuildSyncJob(Branch branch, GitBranchConfiguration configuration) {
        return new BranchJob(branch) {

            @Override
            public String getCategory() {
                return "GitBuildTagSync";
            }

            @Override
            public String getId() {
                return String.valueOf(branch.getId());
            }

            @Override
            public String getDescription() {
                return format(
                        "Git build/tag synchro for branch %s/%s",
                        branch.getProject().getName(),
                        branch.getName()
                );
            }

            @Override
            public int getInterval() {
                return configuration.getBuildTagInterval();
            }

            @Override
            public JobTask createTask() {
                return new RunnableJobTask(info -> buildSync(branch, configuration, info));
            }
        };
    }

    private Job createIndexationJob(GitConfiguration config) {
        return new Job() {
            @Override
            public String getCategory() {
                return "GitIndexation";
            }

            @Override
            public String getId() {
                return config.getGitRepository().getId();
            }

            @Override
            public String getDescription() {
                return format(
                        "Git indexation for %s",
                        config.getName()
                );
            }

            @Override
            public boolean isDisabled() {
                return false;
            }

            @Override
            public int getInterval() {
                return config.getIndexationInterval();
            }

            @Override
            public JobTask createTask() {
                return new RunnableJobTask(
                        info -> index(config, info)
                );
            }
        };
    }

    protected <T> void buildSync(Branch branch, GitBranchConfiguration branchConfiguration, JobInfoListener info) {
        info.post(format("Git build/tag sync for %s/%s", branch.getProject().getName(), branch.getName()));
        GitConfiguration configuration = branchConfiguration.getConfiguration();
        // Gets the branch Git client
        GitRepositoryClient gitClient = gitRepositoryClientFactory.getClient(configuration.getGitRepository());
        // Link
        @SuppressWarnings("unchecked")
        IndexableBuildGitCommitLink<T> link = (IndexableBuildGitCommitLink<T>) branchConfiguration.getBuildCommitLink().getLink();
        @SuppressWarnings("unchecked")
        T linkData = (T) branchConfiguration.getBuildCommitLink().getData();
        // Configuration for the sync
        Property<GitBranchConfigurationProperty> confProperty = propertyService.getProperty(branch, GitBranchConfigurationPropertyType.class);
        boolean override = !confProperty.isEmpty() && confProperty.getValue().isOverride();
        // Makes sure of synchronization
        info.post("Synchronizing before importing");
        gitClient.sync(info::post);
        // FIXME Gets the list of tags
        info.post("Getting list of tags");
        // Collection<GitTag> tags = gitClient.getTags();
        Collection<GitTag> tags = Collections.emptyList();
        // Creates the builds
        info.post("Creating builds from tags");
        for (GitTag tag : tags) {
            String tagName = tag.getName();
            // Filters the tags according to the branch tag pattern
            link.getBuildNameFromTagName(tagName, linkData).ifPresent(buildNameCandidate -> {
                String buildName = NameDescription.escapeName(buildNameCandidate);
                info.post(format("Build %s from tag %s", buildName, tagName));
                // Existing build?
                boolean createBuild;
                Optional<Build> build = structureService.findBuildByName(branch.getProject().getName(), branch.getName(), buildName);
                if (build.isPresent()) {
                    if (override) {
                        // Deletes the build
                        info.post(format("Deleting existing build %s", buildName));
                        structureService.deleteBuild(build.get().getId());
                        createBuild = true;
                    } else {
                        // Keeps the build
                        info.post(format("Build %s already exists", buildName));
                        createBuild = false;
                    }
                } else {
                    createBuild = true;
                }
                // Actual creation
                if (createBuild) {
                    info.post(format("Creating build %s from tag %s", buildName, tagName));
                    structureService.newBuild(
                            Build.of(
                                    branch,
                                    new NameDescription(
                                            buildName,
                                            "Imported from Git tag " + tagName
                                    ),
                                    securityService.getCurrentSignature().withTime(
                                            tag.getTime()
                                    )
                            )
                    );
                }
            });
        }
    }

    private void index(GitConfiguration config, JobInfoListener info) {
        info.post(format("Git sync for %s", config.getName()));
        // Gets the client for this configuration
        GitRepositoryClient client = gitRepositoryClientFactory.getClient(config.getGitRepository());
        // Launches the synchronisation
        client.sync(info::post);
    }

}
