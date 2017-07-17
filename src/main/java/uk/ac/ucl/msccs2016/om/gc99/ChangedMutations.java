package uk.ac.ucl.msccs2016.om.gc99;

import java.util.HashMap;

import static uk.ac.ucl.msccs2016.om.gc99.Worker.SIZE_PIT_MATRIX;

class ChangedMutations {

    String currentCommitHash;
    String parentCommitHash;

    HashMap<String, MutatedFile>[] mutationsStatus;

    @SuppressWarnings("unchecked")
    ChangedMutations(String currentCommitHash, String parentCommitHash){
        this.currentCommitHash = currentCommitHash;
        this.parentCommitHash = parentCommitHash;

        mutationsStatus = (HashMap<String, MutatedFile>[]) new HashMap[SIZE_PIT_MATRIX];

        for (int i =0; i<SIZE_PIT_MATRIX; i++) mutationsStatus[i] = null;

    }
}
