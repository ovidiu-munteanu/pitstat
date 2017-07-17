package uk.ac.ucl.msccs2016.om.gc99;

class LineOfCode {

    String code;
    String diffStatus;

    int newLineNo, oldLineNo;

    LineOfCode(String code, String diffStatus, int newLineNo, int oldLineNo) {
        this.code = code;
        this.diffStatus = diffStatus;
        this.newLineNo = newLineNo;
        this.oldLineNo = oldLineNo;
    }
}
