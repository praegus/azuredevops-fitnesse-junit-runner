package nl.praegus.fitnesse.junit.azuredevops;

import fitnesse.util.TimeMeasurement;
import nl.praegus.azuredevops.javaclient.test.ApiException;
import nl.praegus.azuredevops.javaclient.test.model.TestRun;
import nl.praegus.fitnesse.junit.azuredevops.util.AzureDevopsReporter;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.io.IOException;
import java.util.Properties;

import static java.util.Objects.requireNonNull;
import static nl.praegus.fitnesse.junit.azuredevops.util.Description.getTestName;

public class AzureDevopsRunListener extends RunListener {
    private static final String PROP_TESTRUN = "AZURE_TESTRUN";
    private static final String PROP_TOKEN = "SYSTEM_ACCESSTOKEN";
    private static final String PROP_ORG = "AZURE_ORGANIZATION";
    private static final String PROP_PROJECT = "SYSTEM_PROJECT";
    private static final String PROP_BASEPATH = "BASE_URL";
    private static final String PROP_LOGTAGS = "AZURE_LOG_TAGS";
    private static final String PROPERTY_FILE_NAME = "azureTestRun.properties";

    private TimeMeasurement timeMeasurement;
    private AzureDevopsReporter reporter;

    private int resultCount = 0;

    private TestRun testRun;

    @Override
    public void testStarted(Description description) throws Exception {
        String testName = getTestName(description);

        startRunIfRequired();
        if (testName.equalsIgnoreCase("suitesetup") || testName.equalsIgnoreCase("suiteteardown")) {
            return;
        }
        timeMeasurement = new TimeMeasurement().start();
    }

    private void startRunIfRequired() throws ApiException {
        if (reporter == null) {
            reporter = new AzureDevopsReporter(getProperty(PROP_TOKEN), getProperty(PROP_ORG), getProperty(PROP_PROJECT), getProperty(PROP_BASEPATH), logTags());
        }
        if (testRun == null) {
            testRun = reporter.startTestRun(getProperty(PROP_TESTRUN));
        }
    }

    @Override
    public void testFinished(Description description) throws ApiException {
        String testName = getTestName(description);
        if (testName.equalsIgnoreCase("suitesetup") || testName.equalsIgnoreCase("suiteteardown")) {
            return;
        }
        if (!timeMeasurement.isStopped()) {
            resultCount++;
            reporter.reportTestPassed(description, testRun, getExecutionTimeInMs(timeMeasurement));
        }

    }

    @Override
    public void testFailure(Failure failure) throws ApiException {
        resultCount++;
        reporter.reportTestFailed(failure.getDescription(), failure.getException(), testRun, getExecutionTimeInMs(timeMeasurement));
    }


    @Override
    public void testRunFinished(Result result) throws Exception {
        reporter.finishTestRun(testRun, resultCount, result.getFailureCount());
    }


    protected double getExecutionTimeInMs(TimeMeasurement timer) {
        double executionTime = 0;
        if (timer != null) {
            executionTime = timer.elapsedSeconds();
            if (!timer.isStopped()) {
                timer.stop();
            }
        }
        return executionTime * 1000;
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
            propertyFile.load(requireNonNull(AzureDevopsRunListener.class.getClassLoader().getResourceAsStream(PROPERTY_FILE_NAME)));
            return propertyFile.getProperty(key);
        } catch (NullPointerException | IOException e) {
            if (!key.equals(PROP_BASEPATH)) {
                System.err.println("No value was found for: " + key);
            }
            return null;
        }
    }
}
