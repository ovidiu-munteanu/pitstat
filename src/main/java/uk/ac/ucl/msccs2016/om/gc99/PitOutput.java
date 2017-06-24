package uk.ac.ucl.msccs2016.om.gc99;

import java.util.HashMap;

class PitOutput {
    String commitHash;
    HashMap<String, MutatedFile> mutatedFiles;

    PitOutput(String commitHash, HashMap<String, MutatedFile> mutatedFiles) {
        this.commitHash = commitHash;
        this.mutatedFiles = mutatedFiles;
    }
}
