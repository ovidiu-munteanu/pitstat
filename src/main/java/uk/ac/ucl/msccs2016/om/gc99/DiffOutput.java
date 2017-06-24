package uk.ac.ucl.msccs2016.om.gc99;

import java.util.HashMap;

class DiffOutput {
    String oldCommitHash;
    String newCommitHash;
    HashMap<String, ChangedFile> changedFiles;

    DiffOutput(String oldCommitHash, String newCommitHash, HashMap<String, ChangedFile> changedFiles) {
        this.oldCommitHash = oldCommitHash;
        this.newCommitHash = newCommitHash;
        this.changedFiles = changedFiles;
    }
}
