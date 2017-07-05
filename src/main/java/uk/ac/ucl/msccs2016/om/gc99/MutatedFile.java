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
        String mutator;
        int index;
        KillingTest killingTest;
        String description;

        String changeStatus;

        Mutation(){
            changeStatus = null;
        }
    }

    static class KillingTest {
        String testFile;
        String testMethod;
    }


    HashMap<String, MutatedClass> mutatedClasses;
    String changeType;

    MutatedFile() {
        this.mutatedClasses = new HashMap<>();
        this.changeType = null;
    }

}
