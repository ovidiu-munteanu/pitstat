package uk.ac.ucl.msccs2016.om.gc99;

class LineOfCode {

    String code;
    String status;

    int newLineNo, oldLineNo;

    LineOfCode(String code, String status, int newLineNo, int oldLineNo) {
        this.code = code;
        this.status = status;
        this.newLineNo = newLineNo;
        this.oldLineNo = oldLineNo;
    }
}
