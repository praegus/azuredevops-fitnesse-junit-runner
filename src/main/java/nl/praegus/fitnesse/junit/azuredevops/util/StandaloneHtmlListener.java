package nl.praegus.fitnesse.junit.azuredevops.util;

import fitnesse.testsystems.*;
import org.slf4j.LoggerFactory;

import java.io.Closeable;

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
    private PlainHtmlChunkParser parser = new PlainHtmlChunkParser();

    public static String getOutput() {
        if (output == null) {
            return "";
        }
        String outputString = output.toString();
        return outputString;
    }

    @Override
    public void testSystemStarted(TestSystem testSystem) {
        // niet nodig voor het html bestand
    }

    @Override
    public void testOutputChunk(String chunk) {
        if (!chunk.isEmpty()) {
            chunk = parser.embedImages(chunk);
        }
        output.append(chunk);
    }

    @Override
    public void testStarted(TestPage testPage) {
        output = new StringBuilder();
        output.append(parser.initializeStandalonePage(testPage.getFullPath()));
    }

    @Override
    public void testComplete(TestPage testPage, TestSummary testSummary) {
        parser.finalizeStandalonePage(output);
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
}
