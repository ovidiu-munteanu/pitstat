package uk.ac.ucl.msccs2016.om.gc99;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

class MutatedFile {

    static class MutatedClass {
        HashMap<String, MutatedMethod> mutatedMethods;

        MutatedClass() {
            this.mutatedMethods = new HashMap<>();
        }
    }

    static class MutatedMethod {
        String description;
        List<Mutation> mutations;

        MutatedMethod(String description) {
            this.description = description;
            this.mutations = new ArrayList<>();
        }
    }

    static class Mutation {
        boolean detected;
        String status;
        int lineNo;

        String lineStatus;

        String mutator;
        int index;
        KillingTest killingTest;
        String description;
    }

    static class KillingTest {
        String status;
        String testFileName;
        String testFileChangeStatus;
        String testMethod;
        String testMethodStatus;

        KillingTest() {
            status = "UNKNOWN";
            testMethodStatus = "UNKNOWN";
        }
    }

    String changeStatus;
    HashMap<String, MutatedClass> mutatedClasses;

    MutatedFile() {
        this.changeStatus = null;
        this.mutatedClasses = new HashMap<>();
    }

}
