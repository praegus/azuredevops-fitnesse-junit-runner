# FitNesse Junit run listener for AzureDevOps

### High level
This is a Junit RunListener for use with FitNesse projects that use HsacFitNesseRunner (from https://github.com/fhoeben/hsac-fitnesse-fixtures) to run tests in Azure Devops.

### What does it do?
#### It grabs your test results and reports them using the REST API so they are displayed in Test Plan > Runs and are linked to the pipeline build.

![image](https://user-images.githubusercontent.com/2232710/80232242-de1c6880-8654-11ea-9577-3863912b4294.png)

#### The test run gets the full html report attached as a zip file
![image](https://user-images.githubusercontent.com/2232710/80232392-1c198c80-8655-11ea-8936-9a6b2dbedc71.png)

#### Test cases are reported individually, including a single page result HTML:
![image](https://user-images.githubusercontent.com/2232710/80232466-3eaba580-8655-11ea-840f-fbc2d9fc2f5b.png)

### How to use it
#### Add it as a dependency to your pom.xml:
```
 <dependency>
    <groupId>nl.praegus</groupId>
    <artifactId>toolchain-azuredevops-runlistener</artifactId>
    <version>1.01</version>
    <scope>test</scope>
</dependency>
```
Run your JUnit test class using `AzureDevopsJunitRunner`

In your pipeline: set the necessary environment variables to configure the listener:
``` 
steps:
- task: Maven@3
  env:
    SYSTEM_ACCESSTOKEN: $(System.AccessToken)
    SYSTEM_PROJECT: $(System.TeamProject)
    BUILD_ID: $(Build.BuildId)
    azure.testrun: 'FitNesse Test Run'
    azure.organization: 'My-Azure-Org'
  inputs:
    mavenPomFile: 'pom.xml'
    mavenOptions: '-Xmx3072m'
    javaHomeOption: 'JDKVersion'
    jdkVersionOption: '1.8'
    jdkArchitectureOption: 'x64'
    goals: 'test-compile failsafe:integration-test -DfitnesseSuiteToRun=MyFitnesseTestSuite'
```

The configuration properties can also be set using:
1. System properties (i.e. start test with -Dazure.testrun=MyTestRun)
2. A properrties file in your projects resources

