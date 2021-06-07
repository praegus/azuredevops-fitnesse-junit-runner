package nl.praegus.fitnesse.junit.azuredevops.util;

import fitnesse.testsystems.TestPage;
import net.lingala.zip4j.ZipFile;
import nl.hsac.fitnesse.fixture.Environment;
import nl.praegus.azuredevops.javaclient.test.ApiException;
import nl.praegus.azuredevops.javaclient.test.model.RunCreateModel;
import nl.praegus.azuredevops.javaclient.test.model.RunSummaryModel;
import nl.praegus.azuredevops.javaclient.test.model.RunUpdateModel;
import nl.praegus.azuredevops.javaclient.test.model.ShallowReference;
import nl.praegus.azuredevops.javaclient.test.model.TestAttachmentRequestModel;
import nl.praegus.azuredevops.javaclient.test.model.TestCaseResult;
import nl.praegus.azuredevops.javaclient.test.model.TestRun;
import org.apache.commons.io.IOUtils;
import org.threeten.bp.OffsetDateTime;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static nl.praegus.fitnesse.junit.azuredevops.util.StandaloneHtmlListener.getOutput;

public class AzureDevopsReporter {

    private final AzureDevopsTestRunClientHelper azure;
    private final String org;
    private final String project;
    private final String apiVersion = "5.0-preview";
    public final boolean logTags;

    private final AzureDevopsRunContext context;

    private static final String SCREENSHOT_EXT = "png";
    private static final String PAGESOURCE_EXT = "html";
    private static final Pattern SCREENSHOT_PATTERN = Pattern.compile("href=\"([^\"]*." + SCREENSHOT_EXT + ")\"");
    private static final Pattern PAGESOURCE_PATTERN = Pattern.compile("href=\"([^\"]*." + PAGESOURCE_EXT + ")\"");


    public AzureDevopsReporter(String token, String organization, String project, String basePath, boolean logTags) {
        this.org = organization;
        this.project = project;
        this.logTags = logTags;
        this.context = new AzureDevopsRunContext();

        azure = new AzureDevopsTestRunClientHelper(token, basePath);
    }

    public AzureDevopsRunContext context() {
        return context;
    }

    public TestRun startTestRun(String name) throws ApiException {
        RunCreateModel run = new RunCreateModel();
        RunSummaryModel summary = new RunSummaryModel().testOutcome(RunSummaryModel.TestOutcomeEnum.INPROGRESS).duration(new Integer(0).longValue()).resultCount(0);
        run.setName(name);
        run.setState("InProgress");
        run.setBuild(new ShallowReference().id(System.getenv("BUILD_ID")));
        run.setAutomated(true);
        run.setType("NoConfigRun");
        run.setRunSummary(Collections.singletonList(summary));
        return azure.getRunsApi().runsCreate(org, run, project, apiVersion);
    }

    public int reportTestStarted(TestPage testPage, TestRun run) {
        TestCaseResult result = new TestCaseResult();
        result.setTestCaseTitle(TestPageHelper.getTestName(testPage));
        result.setAutomatedTestName(TestPageHelper.getFullTestName(testPage));
        result.setStartedDate(OffsetDateTime.now());
        result.setState("InProgress");
        result.setBuild(new ShallowReference().id(System.getenv("BUILD_ID")));

        try {
            List<TestCaseResult> testCaseResults = azure.getResultsApi().resultsAdd(org, Collections.singletonList(result), project, run.getId(), apiVersion);
            return testCaseResults.get(0).getId();
        } catch (ApiException e) {
            System.err.println("Error starting test in Azure Devops: " + e.getMessage());
        }
        return -1;
    }

    public void reportTestFinished(TestPage testPage, TestRun run, AzureDevopsRunContext.TestResult testResult, String outcome) {
        TestCaseResult result = new TestCaseResult();
        result.setId(testResult.getId());
        result.setCompletedDate(OffsetDateTime.now());

        result.setOutcome(outcome);
        result.setDurationInMs(testResult.durationInMs());
        result.setErrorMessage(testResult.getErrorMessage());
        result.setState("Completed");

        try {
            addTestResultAttachment(run.getId(), testResult.getId(), new String(Base64.getEncoder().encode(getOutput().getBytes())), TestPageHelper.getTestName(testPage) + ".html");
            azure.getResultsApi().resultsUpdate(org, Collections.singletonList(result), project, run.getId(), apiVersion);
        } catch (ApiException e) {
            System.err.println("Error finishing test in Azure Devops: " + e.getMessage());
        }
        context.updateCounters(outcome);
    }

    public void reportException(TestRun run, int testResultId, String message) {
        TestCaseResult result = new TestCaseResult();
        result.setId(testResultId);
        result.setErrorMessage(message);
        result.setOutcome("Error");

        try {
            azure.getResultsApi().resultsUpdate(org, Collections.singletonList(result), project, run.getId(), apiVersion);
            processFailedTestAttachments(message, Arrays.asList(SCREENSHOT_PATTERN, PAGESOURCE_PATTERN), run.getId(), testResultId);
        } catch (ApiException e) {
            System.err.println("Error writing exception message: " + e.getMessage());
        }
    }


    private void processFailedTestAttachments(String message, List<Pattern> patterns, int runId, int testResultId) throws ApiException {
        if (null != message) {
            for (Pattern pattern : patterns) {
                Matcher patternMatcher = pattern.matcher(message);
                if (patternMatcher.find()) {
                    String filePath = Environment.getInstance().getFitNesseRootDir() + "/" + patternMatcher.group(1);
                    attachFromFileSystem(filePath, runId, testResultId);
                }
            }
        }
    }

    private void addTestResultAttachment(int runId, int testResultId, String base64encodedFileContent, String filename) throws ApiException {
        TestAttachmentRequestModel attachment = new TestAttachmentRequestModel();
        attachment.attachmentType("TmiTestResultDetail");
        attachment.fileName(filename);
        attachment.comment(filename);
        attachment.stream(base64encodedFileContent);

        azure.getAttachmentsApi().attachmentsCreateTestResultAttachment(org, attachment, project, runId, testResultId, apiVersion);
    }

    private void addTestRunAttachment(int runId, String base64encodedFileContent, String filename) throws ApiException {
        TestAttachmentRequestModel attachment = new TestAttachmentRequestModel();
        attachment.attachmentType("TmiTestRunSummary");
        attachment.fileName(filename);
        attachment.comment("Zip containing all test results for this run");
        attachment.stream(base64encodedFileContent);
        azure.getAttachmentsApi().attachmentsCreateTestRunAttachment(org, attachment, project, runId, apiVersion);
    }

    private void attachFromFileSystem(String filePath, int runId, int testResultId) throws ApiException {
        Path path = Paths.get(filePath);
        String filename = path.getFileName().toString();

        byte[] data;
        try {
            data = Files.readAllBytes(path);
            addTestResultAttachment(runId, testResultId, new String(Base64.getEncoder().encode(data)), filename);
        } catch (IOException e) {
            System.err.println("file not found: " + path);
        }

    }

    public void finishTestRun(TestRun testRun) throws Exception {
        RunUpdateModel run = new RunUpdateModel();
        List<RunSummaryModel> stats = new ArrayList<>();
        stats.add(new RunSummaryModel().testOutcome(RunSummaryModel.TestOutcomeEnum.PASSED).resultCount(context.passedTestCount));
        stats.add(new RunSummaryModel().testOutcome(RunSummaryModel.TestOutcomeEnum.FAILED).resultCount(context.failedTestCount));
        stats.add(new RunSummaryModel().testOutcome(RunSummaryModel.TestOutcomeEnum.ERROR).resultCount(context.erroredTestCount));
        stats.add(new RunSummaryModel().testOutcome(RunSummaryModel.TestOutcomeEnum.NOTEXECUTED).resultCount(context.skippedTestCount));

        run.setSubstate(RunUpdateModel.SubstateEnum.NONE);
        run.setState("Completed");
        run.setRunSummary(stats);
        azure.getRunsApi().runsUpdate(org, run, project, testRun.getId(), apiVersion);
        attachFitNesseResultsToRun(testRun.getId());
    }

    private void attachFitNesseResultsToRun(int runId) throws Exception {
        ZipFile testReportBundle = new ZipFile("FitNesseResults.zip");
        testReportBundle.addFolder(new File(Environment.getInstance().getFitNesseRootDir()));
        FileInputStream zipStr = new FileInputStream(testReportBundle.getFile());
        byte[] zippedReport = IOUtils.toByteArray(zipStr);
        addTestRunAttachment(runId, new String(Base64.getEncoder().encode(zippedReport)), "FitnesseResults.zip");
    }

}
