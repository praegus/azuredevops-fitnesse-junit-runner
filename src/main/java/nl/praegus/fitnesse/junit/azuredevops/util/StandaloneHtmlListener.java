package nl.praegus.fitnesse.junit.azuredevops.util;

import fitnesse.testrunner.WikiTestPage;
import fitnesse.testsystems.*;
import fitnesse.wiki.PageData;
import fitnesse.wiki.WikiPageProperty;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * TestSystemListener that keeps a static StringBuilder for the running testcase
 * The sb contains all the concatenated output chunks that are returned from the test system.
 * The html contained in the sb is standalone. No external resources are required. CSS, JS and
 * images (such as screen shots) are embedded in the page.
 * <p>
 * To obtain the html, simply call StandaloneHtmlListener.output from your testrunlistener.
 * Note: this cass is not usable when doing parallel execution from the same workspace.
 */
// todo fix me dit is heel lelijk :-(
public class StandaloneHtmlListener implements TestSystemListener, Closeable {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(StandaloneHtmlListener.class);
    private static StringBuilder output;
    private static String lastSummary;
    private static String[] currentTags;
    private final PlainHtmlChunkParser parser = new PlainHtmlChunkParser();

    public static String getOutput() {
        if (output == null) {
            return "";
        }
        return output.toString();
    }

    @Override
    public void testSystemStarted(TestSystem testSystem) {
        // niet nodig voor het html bestand
    }

    @Override
    public void testOutputChunk(TestPage testPage, String chunk) {
        if (!chunk.isEmpty()) {
            chunk = parser.embedImages(chunk);
        }
        output.append(chunk);
    }

    @Override
    public void testStarted(TestPage testPage) {
        currentTags = new String[]{};
        if (testPage instanceof WikiTestPage) {
            PageData pageData = ((WikiTestPage) testPage).getData();

            if (pageData.getAttribute(WikiPageProperty.SUITES) != null) {
                currentTags = pageData.getAttribute(WikiPageProperty.SUITES).split("\\s*[;,]\\s*");
            }
        }
        output = new StringBuilder();
        output.append(parser.initializeStandalonePage(testPage.getFullPath()));
    }

    @Override
    public void testComplete(TestPage testPage, TestSummary testSummary) {
        parser.finalizeStandalonePage(output);
        lastSummary = testSummary.toString();
    }

    @Override
    public void testSystemStopped(TestSystem testSystem, Throwable cause) {
        // niet nodig voor het html bestand
    }

    @Override
    public void testAssertionVerified(Assertion assertion, TestResult testResult) {
        // niet nodig voor het html bestand
    }

    @Override
    public void testExceptionOccurred(Assertion assertion, ExceptionResult exceptionResult) {
        // niet nodig voor het html bestand
    }

    @Override
    public void close() {
        // niet nodig voor het html bestand
    }

    public static String getLastSummary() {
        String last = lastSummary;
        lastSummary = "";
        return last;
    }

    public static String[] getCurrentTags() {
        return currentTags;
    }
}
