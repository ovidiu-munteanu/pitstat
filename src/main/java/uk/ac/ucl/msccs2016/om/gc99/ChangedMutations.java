package uk.ac.ucl.msccs2016.om.gc99;

import java.util.HashMap;

class ChangedMutations {

    String currentCommitHash;
    String parentCommitHash;

    HashMap<String, MutatedFile> removedMutations;
    HashMap<String, MutatedFile> killedMutations;
    HashMap<String, MutatedFile> survivedMutations;
    HashMap<String, MutatedFile> noCoverageMutations;
    HashMap<String, MutatedFile> nonViableMutations;
    HashMap<String, MutatedFile> timedOutMutations;
    HashMap<String, MutatedFile> memoryErrorMutations;
    HashMap<String, MutatedFile> runErrorMutations;


    ChangedMutations(String currentCommitHash, String parentCommitHash){
        this.currentCommitHash = currentCommitHash;
        this.parentCommitHash = parentCommitHash;
    }
}
