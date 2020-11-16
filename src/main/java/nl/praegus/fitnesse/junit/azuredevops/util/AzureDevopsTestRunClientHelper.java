package nl.praegus.fitnesse.junit.azuredevops.util;

import nl.praegus.azuredevops.javaclient.test.ApiClient;
import nl.praegus.azuredevops.javaclient.test.api.*;

public class AzureDevopsTestRunClientHelper {


    private final ApiClient client;
    private TestPlansApi testPlansApi;
    private TestCasesApi testCasesApi;
    private RunsApi runsApi;
    private ResultsApi resultsApi;
    private AttachmentsApi attachmentsApi;
    private TestPointApi testPointApi;
    private TestSuitesApi suitesApi;


    public AzureDevopsTestRunClientHelper(String token, String baseUrl) {
        if(baseUrl == null) {
            client = new ApiClient();
        } else {
            client = new ApiClient().setBasePath(baseUrl);
        }
        configureClient(token);
    }

    private void configureClient(String token) {
        client.setUsername(token);
        client.setPassword(token);

        testPlansApi = new TestPlansApi(client);
        testCasesApi= new TestCasesApi(client);
        runsApi = new RunsApi(client);
        resultsApi = new ResultsApi(client);
        attachmentsApi = new AttachmentsApi(client);
        testPointApi = new TestPointApi(client);

        suitesApi = new TestSuitesApi(client);
    }

    public RunsApi getRunsApi() {
        return runsApi;
    }

    public ResultsApi getResultsApi() {
        return resultsApi;
    }

    public AttachmentsApi getAttachmentsApi() {
        return attachmentsApi;
    }

    public TestSuitesApi getSuitesApi() {
        return suitesApi;
    }

    public TestPlansApi getTestPlansApi() {
        return testPlansApi;
    }

    public TestCasesApi getTestCasesApi() {
        return testCasesApi;
    }

    public TestPointApi getTestPointApi() {
        return testPointApi;
    }


}
