package nl.praegus.fitnesse.junit.azuredevops.runner;

import fitnesse.junit.DescriptionFactory;
import fitnesse.testrunner.MultipleTestsRunner;
import fitnesse.testrunner.run.TestRun;
import nl.hsac.fitnesse.junit.HsacFitNesseRunner;
import nl.praegus.fitnesse.junit.azuredevops.AzureDevopsRunListener;
import nl.praegus.fitnesse.junit.azuredevops.util.StandaloneHtmlListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;

public class AzureDevopsJunitRunner extends HsacFitNesseRunner {

    public AzureDevopsJunitRunner(Class<?> suiteClass) throws InitializationError {
        super(suiteClass);
    }

    @Override
    protected void runPages(TestRun testRun, RunNotifier notifier) {
        notifier.addListener(new AzureDevopsRunListener());
        super.runPages(testRun, notifier);
    }

    @Override
    protected void addTestSystemListeners(RunNotifier notifier, MultipleTestsRunner testRunner, Class<?> suiteClass, DescriptionFactory descriptionFactory) {
        testRunner.addTestSystemListener(new StandaloneHtmlListener());
        super.addTestSystemListeners(notifier, testRunner, suiteClass, descriptionFactory);
    }
}
