package nl.praegus.fitnesse.junit.azuredevops.listener;

import fitnesse.testsystems.Assertion;
import fitnesse.testsystems.ExceptionResult;
import fitnesse.testsystems.ExecutionResult;
import fitnesse.testsystems.TestPage;
import fitnesse.testsystems.TestResult;
import fitnesse.testsystems.TestSummary;
import fitnesse.testsystems.TestSystem;
import fitnesse.testsystems.TestSystemListener;
import nl.praegus.azuredevops.javaclient.test.ApiException;
import nl.praegus.azuredevops.javaclient.test.model.TestRun;
import nl.praegus.fitnesse.junit.azuredevops.util.AzureDevopsReporter;

import java.io.Closeable;
import java.io.IOException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static fitnesse.testsystems.ExecutionResult.getExecutionResult;
import static java.util.Objects.requireNonNull;
import static nl.praegus.fitnesse.junit.azuredevops.util.StandaloneHtmlListener.getCurrentTags;
import static nl.praegus.fitnesse.junit.azuredevops.util.TestPageHelper.getFullTestName;
import static nl.praegus.fitnesse.junit.azuredevops.util.TestPageHelper.getRelativeName;
import static nl.praegus.fitnesse.junit.azuredevops.util.TestPageHelper.getTestName;

public class AzureDevopsTestSystemListener implements TestSystemListener, Closeable {

    private static final String PROP_TESTRUN = "AZURE_TESTRUN";
    private static final String PROP_TOKEN = "SYSTEM_ACCESSTOKEN";
    private static final String PROP_ORG = "AZURE_ORGANIZATION";
    private static final String PROP_PROJECT = "SYSTEM_PROJECT";
    private static final String PROP_BASEPATH = "BASE_URL";
    private static final String PROP_LOGTAGS = "AZURE_LOG_TAGS";
    private static final String PROPERTY_FILE_NAME = "azureTestRun.properties";

    private static final Pattern METHOD_PATTERN = Pattern.compile(".*methodName='(.*)'.*", Pattern.DOTALL);
    private static final Pattern ARGS_PATTERN = Pattern.compile(".*args=\\[(.*)].*", Pattern.DOTALL);

    private AzureDevopsReporter reporter;

    private TestRun testRun;

    @Override
    public void close() {
    }

    @Override
    public void testSystemStarted(TestSystem testSystem) {
        startRunIfRequired();
    }

    @Override
    public void testOutputChunk(TestPage testPage, String output) {

    }

    @Override
    public void testStarted(TestPage testPage) {
        String testName = getTestName(testPage);
        if (testName.equalsIgnoreCase("suitesetup") || testName.equalsIgnoreCase("suiteteardown")) {
            return;
        }
        reporter.context().addTestResult(getFullTestName(testPage), reporter.reportTestStarted(testPage, testRun));
        if (reporter.logTags && getCurrentTags().length > 0) {
            reporter.context().getCurrentTestResult().appendError("[" + String.join(", ", getCurrentTags()) + "]");
        }

    }

    @Override
    public void testComplete(TestPage testPage, TestSummary testSummary) {
        String testName = getTestName(testPage);
        ExecutionResult result = getExecutionResult(getRelativeName(testPage), testSummary);
        if (testName.equalsIgnoreCase("suitesetup") || testName.equalsIgnoreCase("suiteteardown")) {
            return;
        }

        String resultStr = status(result);

        reporter.reportTestFinished(testPage, testRun, reporter.context().getResult(getFullTestName(testPage)), resultStr);
    }

    @Override
    public void testSystemStopped(TestSystem testSystem, Throwable cause) {
        try {
            reporter.finishTestRun(testRun);
        } catch (Exception e) {
            System.err.println("Error finishing testrun in Azure DevOps: " + e.getMessage());
        }
    }

    @Override
    public void testAssertionVerified(Assertion assertion, TestResult testResult) {
        if (reporter.context().getCurrentTestResult() != null && testResult != null && testResult.getExecutionResult() != null) {
            String resultStr = status(testResult.getExecutionResult());
            if (!resultStr.equalsIgnoreCase("passed")) {
                String instructionIfo = assertion.toString();

                String method = "";
                String args = "";

                Matcher m_method = METHOD_PATTERN.matcher(instructionIfo);
                if (m_method.matches()) {
                    method = m_method.group(1);
                }
                Matcher m_args = ARGS_PATTERN.matcher(instructionIfo);
                if (m_args.matches()) {
                    args = m_args.group(1);
                }

                reporter.context().getCurrentTestResult().appendError(String.format("[%s(%s)] => %s", method, args, createMessageForAssertion(testResult)));
            }
        }
    }

    @Override
    public void testExceptionOccurred(Assertion assertion, ExceptionResult exceptionResult) {
        if (reporter.context().getCurrentTestResult() != null) { //If null, no test was started (Top level SuiteSetUp Exception)
            String instructionIfo = assertion.toString();
            String method = "";
            String args = "";

            Matcher m_method = METHOD_PATTERN.matcher(instructionIfo);
            if (m_method.matches()) {
                method = m_method.group(1);
            }
            Matcher m_args = ARGS_PATTERN.matcher(instructionIfo);
            if (m_args.matches()) {
                args = m_args.group(1);
            }

            reporter.reportException(testRun, reporter.context().getCurrentTestResult().getId(),
                    String.format("[%s(%s)] => %s", method, args, exceptionResult.getMessage()));
        }
    }


    private void startRunIfRequired() {
        if (reporter == null) {
            reporter = new AzureDevopsReporter(getProperty(PROP_TOKEN), getProperty(PROP_ORG), getProperty(PROP_PROJECT), getProperty(PROP_BASEPATH), logTags());
        }
        if (testRun == null) {
            try {
                testRun = reporter.startTestRun(getProperty(PROP_TESTRUN));
            } catch (ApiException e) {
                System.err.printf("ERROR Starting AzureDevops Testrun: %s%n", e.getMessage());
            }
        }
    }

    private boolean logTags() {
        String logTagsValue = getProperty(PROP_LOGTAGS);
        return logTagsValue != null && logTagsValue.equalsIgnoreCase("true");
    }

    private String getProperty(String key) {
        if (System.getenv(key) != null) {
            return System.getenv(key);
        }
        if (System.getProperty(key) != null) {
            return System.getProperty(key);
        }

        Properties propertyFile = new Properties();
        try {
            propertyFile.load(requireNonNull(AzureDevopsTestSystemListener.class.getClassLoader().getResourceAsStream(PROPERTY_FILE_NAME)));
            return propertyFile.getProperty(key);
        } catch (NullPointerException | IOException e) {
            if (!key.equals(PROP_BASEPATH)) {
                System.err.println("No value was found for: " + key);
            }
            return null;
        }
    }

    private String status(ExecutionResult result) {
        switch (result) {
            case ERROR:
                return "Error";
            case FAIL:
                return "Failed";
            case IGNORE:
                return "NotExecuted";
            default:
                return "Passed";
        }
    }

    private String createMessageForAssertion(TestResult testResult) {
        if (testResult.hasActual() && testResult.hasExpected()) {
            return String.format("[%s] expected [%s]", testResult.getActual(), testResult.getExpected());
        } else {
            return (testResult.hasActual() || testResult.hasExpected()) && testResult.hasMessage() ? String.format("[%s] %s", testResult.hasActual() ? testResult.getActual() : testResult.getExpected(), testResult.getMessage()) : testResult.getMessage();
        }
    }
}
