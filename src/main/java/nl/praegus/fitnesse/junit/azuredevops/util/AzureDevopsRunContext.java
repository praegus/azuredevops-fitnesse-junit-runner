package nl.praegus.fitnesse.junit.azuredevops.util;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

public class AzureDevopsRunContext {
    private final Map<String, TestResult> testResults = new HashMap<>();
    private TestResult currentTestResult;
    public int passedTestCount = 0;
    public int failedTestCount = 0;
    public int erroredTestCount = 0;
    public int skippedTestCount = 0;


    public void addTestResult(String fullName, int id) {
        TestResult r = new TestResult(id);
        testResults.put(fullName, r);
        currentTestResult = r;
    }

    public TestResult getResult(String fullName) {
        return testResults.getOrDefault(fullName, new TestResult(-1));
    }

    public TestResult getCurrentTestResult() {
        return currentTestResult;
    }

    public void updateCounters(String outcome) {
        switch (outcome) {
            case "Failed":
                failedTestCount++;
                break;
            case "Error":
                erroredTestCount++;
                break;
            case "NotExecuted":
                skippedTestCount++;
                break;
            case "Passed":
                passedTestCount++;
                break;
        }
    }

    public static class TestResult {

        private final int id;
        private final OffsetDateTime start;
        private String errorMessage = "";

        TestResult(int id) {
            this.id = id;
            this.start = OffsetDateTime.now();
        }

        public int getId() {
            return id;
        }

        public void appendError(String msg) {
            errorMessage += msg + "; \r\n";
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public double durationInMs() {
            return ChronoUnit.MILLIS.between(start, OffsetDateTime.now());
        }
    }
}