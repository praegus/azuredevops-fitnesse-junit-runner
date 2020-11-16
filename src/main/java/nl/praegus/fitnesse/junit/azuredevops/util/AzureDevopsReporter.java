package nl.praegus.fitnesse.junit.azuredevops.util;

import com.overzealous.remark.Remark;
import net.lingala.zip4j.ZipFile;
import nl.hsac.fitnesse.fixture.Environment;
import nl.praegus.azuredevops.javaclient.test.ApiException;
import nl.praegus.azuredevops.javaclient.test.model.*;
import org.apache.commons.io.IOUtils;
import org.junit.runner.Description;
import org.threeten.bp.OffsetDateTime;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static nl.praegus.fitnesse.junit.azuredevops.util.Description.getFullTestName;
import static nl.praegus.fitnesse.junit.azuredevops.util.Description.getTestName;

public class AzureDevopsReporter {
    private static final String propertyFileName = "azureTestRun.properties";

    private AzureDevopsTestRunClientHelper azure;
    private String org;
    private String project;
    private String apiVersion = "5.0-preview";

    private static final String SCREENSHOT_EXT = "png";
    private static final String PAGESOURCE_EXT = "html";
    private static final Pattern SCREENSHOT_PATTERN = Pattern.compile("href=\"([^\"]*." + SCREENSHOT_EXT + ")\"");
    private static final Pattern PAGESOURCE_PATTERN = Pattern.compile("href=\"([^\"]*." + PAGESOURCE_EXT + ")\"");


    public AzureDevopsReporter(String token, String organization, String project, String basePath) {
        this.org = organization;
        this.project = project;
        azure = new AzureDevopsTestRunClientHelper(token, basePath);
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

    public void reportTestPassed(Description description, TestRun run, Double executionTime) throws ApiException {
        TestCaseResult result = completedTestCaseBase(description, run, executionTime);
        result.setOutcome("Passed");
        List<TestCaseResult> testCaseResults = azure.getResultsApi().resultsAdd(org, Collections.singletonList(result), project, run.getId(), apiVersion);

        addTestResultAttachment(run.getId(), testCaseResults.get(0).getId(), new String(Base64.getEncoder().encode(StandaloneHtmlListener.getOutput().getBytes())), getTestName(description) + ".html");
    }

    public void reportTestFailed(Description description, Throwable exception, TestRun run, Double executionTime) throws ApiException {
        String errorMsg = exception.getMessage().contains("</") ? new Remark().convert(exception.getMessage()): exception.getMessage();
        TestCaseResult result = completedTestCaseBase(description, run, executionTime);

        result.setOutcome("Failed");
        result.setErrorMessage(errorMsg);

        List<TestCaseResult> testCaseResults = azure.getResultsApi().resultsAdd(org, Collections.singletonList(result), project, run.getId(), apiVersion);

        List<Pattern> patterns = new ArrayList<>();
        patterns.add(SCREENSHOT_PATTERN);
        patterns.add(PAGESOURCE_PATTERN);
        processFailedTestAttachments(exception, patterns, run.getId(), testCaseResults.get(0).getId());

        addTestResultAttachment(run.getId(), testCaseResults.get(0).getId(), new String(Base64.getEncoder().encode(StandaloneHtmlListener.getOutput().getBytes())), getTestName(description) + ".html");

    }

    private TestCaseResult completedTestCaseBase(Description description, TestRun run, Double executionTime) {
        OffsetDateTime now = OffsetDateTime.now();
        TestCaseResult result = new TestCaseResult();
        result.setTestCaseTitle(getTestName(description));
        result.setAutomatedTestName(getFullTestName(description));
        result.setDurationInMs(executionTime);
        result.setStartedDate(now.minusNanos(executionTime.longValue() * 1000000));
        result.setCompletedDate(now);
        result.setState("Completed");
        result.setBuild(new ShallowReference().id(System.getenv("BUILD_ID")));
        return result;
    }

    private void processFailedTestAttachments(Throwable ex, List<Pattern> patterns, int runId, int testResultId) throws ApiException {
        if (null != ex.getMessage()) {
            for (Pattern pattern : patterns) {
                Matcher patternMatcher = pattern.matcher(ex.getMessage());
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
        attachment.stream(base64encodedFileContent);

        azure.getAttachmentsApi().attachmentsCreateTestResultAttachment(org, attachment, project, runId, testResultId, apiVersion);
    }

    private void addTestRunAttachment(int runId, String base64encodedFileContent, String filename) throws ApiException {
        TestAttachmentRequestModel attachment = new TestAttachmentRequestModel();
        attachment.attachmentType("TmiTestRunSummary");
        attachment.fileName(filename);
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
            System.err.println("file not found: " + path.toString());
        }

    }

    public void finishTestRun(TestRun testRun, int resultCount, int failCount, Double executionTime) throws Exception {
        RunUpdateModel run = new RunUpdateModel();
        List<RunSummaryModel> stats = new ArrayList<>();
        stats.add(new RunSummaryModel().testOutcome(RunSummaryModel.TestOutcomeEnum.PASSED).resultCount(resultCount));
        stats.add(new RunSummaryModel().testOutcome(RunSummaryModel.TestOutcomeEnum.FAILED).resultCount(failCount));

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
