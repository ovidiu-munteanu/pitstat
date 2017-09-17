package uk.ac.ucl.msccs2016.om.gc99;

import java.util.HashMap;

/**
 *
 */
class PitOutput {
    String commitHash;
    HashMap<String, MutatedFile> mutatedFiles;

    /**
     *
     * @param commitHash
     * @param mutatedFiles
     */
    PitOutput(String commitHash, HashMap<String, MutatedFile> mutatedFiles) {
        this.commitHash = commitHash;
        this.mutatedFiles = mutatedFiles;
    }

    /**
     *
     * @return
     */
    PitOutput getClone() {
        return (PitOutput) JSONHandler.cloneObject(this);
    }
}
