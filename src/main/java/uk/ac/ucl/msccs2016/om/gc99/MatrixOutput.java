package uk.ac.ucl.msccs2016.om.gc99;

public class MatrixOutput {
    String newCommitHash;
    String oldCommitHash;
    int[][] pitMatrix;

    public MatrixOutput(String newCommitHash, String oldCommitHash, int[][] pitMatrix) {
        this.newCommitHash = newCommitHash;
        this.oldCommitHash = oldCommitHash;
        this.pitMatrix = pitMatrix;
    }
}
