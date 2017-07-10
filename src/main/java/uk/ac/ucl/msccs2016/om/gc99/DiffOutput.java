package uk.ac.ucl.msccs2016.om.gc99;

import java.util.HashMap;

class DiffOutput {
    String newCommitHash;
    String oldCommitHash;
    HashMap<String, ChangedFile> changedFiles;

    DiffOutput(String oldCommitHash, String newCommitHash, HashMap<String, ChangedFile> changedFiles) {
        this.newCommitHash = newCommitHash;
        this.oldCommitHash = oldCommitHash;
        this.changedFiles = changedFiles;
    }
}
