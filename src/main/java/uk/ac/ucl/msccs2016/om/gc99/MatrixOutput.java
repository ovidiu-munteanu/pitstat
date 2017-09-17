package uk.ac.ucl.msccs2016.om.gc99;

/**
 *
 */
class MatrixOutput {
    String currentCommitHash;
    String parentCommitHash;
    int[][] pitMatrix;

    /**
     *
     */
    MatrixOutput() {
    }

    /**
     *
     * @param currentCommitHash
     * @param parentCommitHash
     * @param pitMatrix
     */
    MatrixOutput(String currentCommitHash, String parentCommitHash, int[][] pitMatrix) {
        this.currentCommitHash = currentCommitHash;
        this.parentCommitHash = parentCommitHash;
        this.pitMatrix = pitMatrix;
    }
}
