package uk.ac.ucl.msccs2016.om.gc99;

import java.util.HashMap;

/**
 *
 */
class DiffOutput {
    String currentCommitHash, parentCommitHash;
    HashMap<String, ChangedFile> changedFiles;

    /**
     *
     */
    DiffOutput() {
    }

    /**
     *
     * @param currentCommitHash
     * @param parentCommitHash
     * @param changedFiles
     */
    DiffOutput(String currentCommitHash, String parentCommitHash,
               HashMap<String, ChangedFile> changedFiles) {
        this.currentCommitHash = currentCommitHash;
        this.parentCommitHash = parentCommitHash;
        this.changedFiles = changedFiles;
    }
}
