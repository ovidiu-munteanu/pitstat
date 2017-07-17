package uk.ac.ucl.msccs2016.om.gc99;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

class MutatedFile {

    String oldFileName;
    String fileDiffStatus;
    HashMap<String, MutatedClass> mutatedClasses;

    static class MutatedClass {
        HashMap<String, MutatedMethod> mutatedMethods;
    }

    static class MutatedMethod {
        String description;
        String description_old;
        List<Mutation> mutations;

        MutatedMethod() {
            mutations = new ArrayList<>();
        }
    }

    static class Mutation {
        String mutationStatus;
        Boolean detected, detected_old;
        String pitStatus, pitStatus_old;
        Integer lineNo, lineNo_old;
        String lineDiffStatus;
        String mutator, mutator_old;
        Integer index, index_old;
        KillingTest killingTest;
        String description, description_old;
    }

    static class KillingTest {

        class TestFile {
            String fileName, fileName_old, diffStatus;

            String testMethod;
        }

        String testStatus, testStatus_old;
        // testStatus records the status of the killing test with respect to the parent commit:
        //      "NEW"       the mutation was not killed in the parent commit
        //      "UNCHANGED" the test file and the test method have not changed
        //      "CHANGED"   the test file / test method, or both have changed / been modified
        //      "REGRESSED" potential code regression, i.e. mutation was killed in parent commit but is NO LONGER
        //                  killed in current commit
        //      null        the mutation did not exist in the parent commit
        String regressionNote;

        String testFileStatus, testFileStatus_old;
        // testFileStatus records the status of the test file which generated the killing test for this mutation in the
        // parent commit, i.e. is it the same test file or a different test file:
        //      "CHANGED"   different test file
        //      "UNCHANGED" same test file
        //      null        testStatus is "NEW" or mutation did not exist in parent commit
        // NOTE: this is NOT the same as the test file diff status and does not record whether the test file itself has
        // changed in any way; it strictly indicates whether it is the same file that generated a killing test for this
        // mutation in the parent commit or is it a different one


        String testMethodStatus, testMethodStatus_old;
        // testMethodStatus records the status of the test method which generated the killing test for this mutation in
        // the parent commit, i.e. is it the same test method or a different test method:
        //      "UNCHANGED" same test file, same method name
        //      "CHANGED"   same test file, different method name
        //      "UNKNOWN"   different / modified test file and/or method
        //      null        testStatus is "NEW" or "REGRESSED", or mutation did not exist in parent commit
        // NOTE 1: this only indicates whether the killing method name is the same as in the parent commit; it does not
        // indicate whether the method contents have changed in any way
        // NOTE 2: to avoid misinterpretation, in case the test file has changed in any way, i.e. it has been modified
        // or is a different test file, testMethodStatus is set to "UNKNOWN"


        TestFile testFile, testFile_old;
        // if this is a new killing test, i.e. the mutation was not killed in the parent commit, then testFile_old is
        // null (not explicitly initialised)

        KillingTest() {
            testFile = new TestFile();
        }
    }
}