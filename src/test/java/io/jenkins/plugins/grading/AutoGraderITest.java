package io.jenkins.plugins.grading;

import java.io.IOException;
import java.util.List;

import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import hudson.model.Result;
import hudson.model.Run;

import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerSuite;

import static io.jenkins.plugins.grading.assertions.Assertions.*;

/**
 * Integration tests for the {@link AutoGrader} step.
 *
 * @author Ullrich Hafner
 */
public class AutoGraderITest extends IntegrationTestWithJenkinsPerSuite {
    /** Verifies that the step skips all autograding parts if the configuration is empty. */
    @Test
    public void shouldSkipGradingIfConfigurationIsEmpty() {
        WorkflowJob job = createPipelineWithWorkspaceFiles("checkstyle.xml");

        configureScanner(job, "checkstyle", "{}");
        Run<?, ?> baseline = buildSuccessfully(job);

        assertThat(getConsoleLog(baseline)).contains("[Autograding] Skipping static analysis results");
        assertThat(getConsoleLog(baseline)).contains("[Autograding] Skipping test results");
        assertThat(getConsoleLog(baseline)).contains("[Autograding] Skipping coverage results");
        assertThat(getConsoleLog(baseline)).contains("[Autograding] Skipping mutation coverage results");
    }

    /**
     * Verifies that an {@link IllegalArgumentException} is thrown if testing has been requested, but no testing action has been recorded.
     */
    @Test
    public void shouldAbortBuildSinceNoTestActionHasBeenRegistered() {
        WorkflowJob job = createPipelineWithWorkspaceFiles("checkstyle.xml");

        configureScanner(job, "checkstyle", "{\"tests\":{\"maxScore\":100,\"passedImpact\":1,\"failureImpact\":-5,\"skippedImpact\":-1}}");
        Run<?, ?> baseline = buildWithResult(job, Result.FAILURE);

        assertThat(getConsoleLog(baseline)).contains("java.lang.IllegalArgumentException: Test scoring has been enabled, but no test results have been found.");
    }

    /**
     * Verifies that CheckStyle results are correctly graded.
     */
    @Test
    public void shouldCountCheckStyleWarnings() {
        WorkflowJob job = createPipelineWithWorkspaceFiles("checkstyle.xml");

        configureScanner(job, "checkstyle", "{\"analysis\":{\"maxScore\":100,\"errorImpact\":-10,\"highImpact\":-5,\"normalImpact\":-2,\"lowImpact\":-1}}");
        Run<?, ?> baseline = buildSuccessfully(job);

        assertThat(getConsoleLog(baseline)).contains("[Autograding] Grading static analysis results for CheckStyle");
        assertThat(getConsoleLog(baseline)).contains(
                "[Autograding] -> Score -60 (warnings distribution err:6, high:0, normal:0, low:0)");
        assertThat(getConsoleLog(baseline)).contains("[Autograding] Total score for static analysis results: 40");

        List<AutoGradingBuildAction> actions = baseline.getActions(AutoGradingBuildAction.class);
        assertThat(actions).hasSize(1);
        AggregatedScore score = actions.get(0).getResult();

        assertThat(score).hasAchieved(40);
    }

    @Test
    public void shouldGradeTestResults() {
        String fileName = "test-successful.xml";
        WorkflowJob job = createPipelineWithWorkspaceFiles(fileName);

        configureTestScanner(job, fileName,
                "{\"tests\":{\"maxScore\":2,\"passedImpact\":1,\"failureImpact\":-5,\"skippedImpact\":-1}}");
        Run<?, ?> baseline = buildSuccessfully(job);

        assertThat(getConsoleLog(baseline)).contains("[Autograding] Grading test results ");
        assertThat(getConsoleLog(baseline)).contains(
                "[Autograding] -> Score 2 - from recorded test results: 2, 2, 0, 0");
        assertThat(getConsoleLog(baseline)).contains("[Autograding] Total score for test results: 2");

        List<AutoGradingBuildAction> actions = baseline.getActions(AutoGradingBuildAction.class);
        assertThat(actions).hasSize(1);
        AggregatedScore score = actions.get(0).getResult();

        assertThat(score).hasAchieved(2);
    }

    @Test
    public void shouldGradeTestResultsWithAssertionError() {
        String fileName = "test-assertion-error.xml";
        WorkflowJob job = createPipelineWithWorkspaceFiles(fileName);

        configureTestScanner(job, fileName,
                "{\"tests\":{\"maxScore\":100,\"passedImpact\":1,\"failureImpact\":-5,\"skippedImpact\":-1}}");
        Run<?, ?> baseline = buildWithResult(job, Result.UNSTABLE);

        assertThat(getConsoleLog(baseline)).contains("[Autograding] Grading test results ");
        assertThat(getConsoleLog(baseline)).contains(
                "[Autograding] -> Score -10 - from recorded test results: 2, 0, 2, 0");
        assertThat(getConsoleLog(baseline)).contains("[Autograding] Total score for test results: 90");

        List<AutoGradingBuildAction> actions = baseline.getActions(AutoGradingBuildAction.class);
        assertThat(actions).hasSize(1);
        AggregatedScore score = actions.get(0).getResult();

        assertThat(score).hasAchieved(90);
    }

    @Test
    public void shouldGradeTestResultsWithAssertionErrorAndSkipTest() {
        String fileName = "test-assertion-error-with-skip.xml";
        WorkflowJob job = createPipelineWithWorkspaceFiles(fileName);

        configureTestScanner(job, fileName,
                "{\"tests\":{\"maxScore\":100,\"passedImpact\":1,\"failureImpact\":-5,\"skippedImpact\":-1}}");
        Run<?, ?> baseline = buildWithResult(job, Result.UNSTABLE);

        assertThat(getConsoleLog(baseline)).contains("[Autograding] Grading test results ");
        assertThat(getConsoleLog(baseline)).contains(
                "[Autograding] -> Score -5 - from recorded test results: 3, 1, 1, 1");
        assertThat(getConsoleLog(baseline)).contains("[Autograding] Total score for test results: 95");

        List<AutoGradingBuildAction> actions = baseline.getActions(AutoGradingBuildAction.class);
        assertThat(actions).hasSize(1);
        AggregatedScore score = actions.get(0).getResult();

        assertThat(score).hasAchieved(95);
    }

    @Test
    public void shouldGradeCoverageResults() {
        String fileName = "coverage.xml";
        WorkflowJob job = createPipelineWithWorkspaceFiles(fileName);

        configureCoverageScanner(job, fileName,
                "{\"coverage\": {\"maxScore\": 100,\"coveredImpact\": 1,\"missedImpact\": -1}}");
        Run<?, ?> baseline = buildSuccessfully(job);

        assertThat(getConsoleLog(baseline)).contains("[Autograding] Grading coverage results ");
        assertThat(getConsoleLog(baseline)).contains(
                "[Autograding] -> Score 100 - from recorded line coverage results: 100%");
        assertThat(getConsoleLog(baseline)).contains(
                "[Autograding] -> Score 58 - from recorded branch coverage results: 79%");
        assertThat(getConsoleLog(baseline)).contains("[Autograding] Total score for coverage results: 100");

        List<AutoGradingBuildAction> actions = baseline.getActions(AutoGradingBuildAction.class);
        assertThat(actions).hasSize(1);
        AggregatedScore score = actions.get(0).getResult();

        assertThat(score).hasAchieved(100);
    }

    /**
     * Returns the console log as a String.
     *
     * @param build
     *         the build to get the log for
     *
     * @return the console log
     */
    protected String getConsoleLog(final Run<?, ?> build) {
        try {
            return JenkinsRule.getLog(build);
        }
        catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private void configureScanner(final WorkflowJob job, final String fileName, final String configuration) {
        job.setDefinition(new CpsFlowDefinition("node {\n"
                + "  stage ('Integration Test') {\n"
                + "         recordIssues tool: checkStyle(pattern: '**/" + fileName + "*')\n"
                + "         autoGrade('" + configuration + "')\n"
                + "  }\n"
                + "}", true));
    }

    private void configureTestScanner(final WorkflowJob job, final String fileName, final String configuration) {
        job.setDefinition(new CpsFlowDefinition("node {\n"
                + "  stage ('Build and Static Analysis') {\n"
                + "         junit testResults: '**/" + fileName + "'\n"
                + "         autoGrade('" + configuration + "')\n"
                + "  }\n"
                + "}", true));
    }

    private void configureCoverageScanner(final WorkflowJob job, final String fileName, final String configuration) {
        job.setDefinition(new CpsFlowDefinition("node {\n"
                + "  stage ('Integration Test') {\n"
                + "     publishCoverage adapters: [jacocoAdapter('**/" + fileName + "*')]\n"
                + "     autoGrade('" + configuration + "')\n"
                + "  }\n"
                + "}", true));
    }

}