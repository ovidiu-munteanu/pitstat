package uk.ac.ucl.msccs2016.om.gc99;

public class MatrixOutput {
    String currentCommitHash;
    String parentCommitHash;
    int[][] pitMatrix;

    public MatrixOutput(){

    }

    public MatrixOutput(String currentCommitHash, String parentCommitHash, int[][] pitMatrix) {
        this.currentCommitHash = currentCommitHash;
        this.parentCommitHash = parentCommitHash;
        this.pitMatrix = pitMatrix;
    }
}
