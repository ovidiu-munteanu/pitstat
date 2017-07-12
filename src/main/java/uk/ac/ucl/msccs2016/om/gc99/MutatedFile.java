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


        Mutation() {
            lineStatus = null;
        }

        boolean equals(Mutation mutation) {
            return index == mutation.index &&
                    mutator.equals(mutation.mutator) &&
                    description.equals(mutation.description)
//                  && lineStatus.equals("UNCHANGED")
//                  && lineNo == mutation.lineNo
                    ;
        }

//        void setKillingTestStatus(KillingTest oldKillingTest) {
//            if (oldKillingTest != null) {
//
//
//
//
//            } else {
//                killingTest.status = "NEW";
//            }
//        }

    }

    static class KillingTest {
        String status;
        String testFileName;
        String testFileChangeType;
        String testMethod;
        String testMethodStatus;

        KillingTest() {
            status = "UNKNOWN";
            testMethodStatus = "UNKNOWN";
        }

        boolean equals(KillingTest killingTest) {
            return testFileName.equals(killingTest.testFileName) &&
                    testMethod.equals(killingTest.testMethod);
        }
    }

    String changeType;
    HashMap<String, MutatedClass> mutatedClasses;

    MutatedFile() {
        this.changeType = null;
        this.mutatedClasses = new HashMap<>();
    }

}
