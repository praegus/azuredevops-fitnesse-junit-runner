package nl.praegus.fitnesse.junit.azuredevops.util;

public class Description {

        private Description() {
            // in order to hide
        }

        public static String getFullTestName(org.junit.runner.Description description) {
            return description.getDisplayName().replaceAll("\\(.+\\)", "");
        }

        public static String getTestName(org.junit.runner.Description description) {
            String[] testNameParts = getFullTestName(description).split("\\.");
            return testNameParts[testNameParts.length - 1];
        }

        public static String getSuiteName(org.junit.runner.Description description) {
            String[] testNameParts = getFullTestName(description).split("\\.");
            if (testNameParts.length > 1) {
                return testNameParts[testNameParts.length - 2];
            } else {
                return "default";
            }
        }
    }
