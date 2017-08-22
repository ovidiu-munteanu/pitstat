package uk.ac.ucl.msccs2016.om.gc99;

interface Worker {

     String ANSI_RESET = "\u001B[0m";
     String ANSI_BLACK = "\u001B[30m";
     String ANSI_RED = "\u001B[31m";
     String ANSI_GREEN = "\u001B[32m";
     String ANSI_YELLOW = "\u001B[33m";
     String ANSI_BLUE = "\u001B[34m";
     String ANSI_PURPLE = "\u001B[35m";
     String ANSI_CYAN = "\u001B[36m";
     String ANSI_WHITE = "\u001B[37m";

     String ANSI_BLACK_BG = "\u001B[40m";
     String ANSI_RED_BG = "\u001B[41m";
     String ANSI_GREEN_BG = "\u001B[42m";
     String ANSI_YELLOW_BG = "\u001B[43m";
     String ANSI_BLUE_BG = "\u001B[44m";
     String ANSI_PURPLE_BG = "\u001B[45m";
     String ANSI_CYAN_BG = "\u001B[46m";
     String ANSI_WHITE_BG = "\u001B[47m";


     boolean PRETTY_PRINTING = true;

     String TEMP_DIRECTORY = System.getProperty("java.io.tmpdir");
     String FILE_SEPARATOR = System.getProperty("file.separator");

     String POM_FILE = "pom.xml";

     String MAVEN_HOME = "M2_HOME";
     String MAVEN_JAVA_MAIN_SRC_PATH = "src/main/java";
     String MAVEN_JAVA_TEST_SRC_PATH = "src/test/java";

     String PIT_REPORTS_PATH = "target/pit-reports";
     String PIT_MUTATIONS_FILE = "mutations.xml";

     String TIMESTAMP_PATTERN = "yyyyMMddHHmmss";

     String TYPE_DIFF_HUMAN_OUTPUT = "difH";
     String TYPE_DIFF_MACHINE_OUTPUT = "difM";
     String TYPE_PIT_MACHINE_OUTPUT = "pitM";
     String TYPE_MATRIX_HUMAN_OUTPUT = "mtxH";
     String TYPE_MATRIX_MACHINE_OUTPUT = "mtxM";
     String TYPE_CHANGES_MACHINE_OUTPUT = "chmM";

     String SEPARATOR = "-";
     String TIMESTAMP_PLACEHOLDER = "<timestamp>";
     String HASH_PLACEHOLDER = "<hash>";
     String JSON_EXTENSION = ".json";
     String TEXT_EXTENSION = ".txt";
     String ZIP_EXTENSION = ".zip";

     String DIFF_HUMAN_OUTPUT_BASE_FILE_NAME = TYPE_DIFF_HUMAN_OUTPUT + SEPARATOR + TIMESTAMP_PLACEHOLDER + SEPARATOR + HASH_PLACEHOLDER + TEXT_EXTENSION;
     String MATRIX_HUMAN_OUTPUT_BASE_FILE_NAME = TYPE_MATRIX_HUMAN_OUTPUT + SEPARATOR + TIMESTAMP_PLACEHOLDER + SEPARATOR + HASH_PLACEHOLDER + TEXT_EXTENSION;

     String DIFF_MACHINE_OUTPUT_BASE_FILE_NAME = TYPE_DIFF_MACHINE_OUTPUT + SEPARATOR + TIMESTAMP_PLACEHOLDER + SEPARATOR + HASH_PLACEHOLDER + JSON_EXTENSION;
     String MATRIX_MACHINE_OUTPUT_BASE_FILE_NAME = TYPE_MATRIX_MACHINE_OUTPUT + SEPARATOR + TIMESTAMP_PLACEHOLDER + SEPARATOR + HASH_PLACEHOLDER + JSON_EXTENSION;
     String PIT_MACHINE_OUTPUT_BASE_FILE_NAME = TYPE_PIT_MACHINE_OUTPUT + SEPARATOR + TIMESTAMP_PLACEHOLDER + SEPARATOR + HASH_PLACEHOLDER + JSON_EXTENSION;

     String CHANGES_MACHINE_OUTPUT_BASE_FILE_NAME = TYPE_CHANGES_MACHINE_OUTPUT + SEPARATOR + TIMESTAMP_PLACEHOLDER + SEPARATOR + HASH_PLACEHOLDER + JSON_EXTENSION;

     String MVN_GOAL_CLEAN = " clean ";
     String MVN_GOAL_TEST = " test ";
     String MVN_GOAL_PITEST = " org.pitest:pitest-maven:mutationCoverage ";

     int
            ROW_COL_NON_EXISTENT = 0,
            ROW_COL_KILLED = 1,
            ROW_COL_SURVIVED = 2,
            ROW_COL_NO_COVERAGE = 3,
            ROW_COL_NON_VIABLE = 4,
            ROW_COL_TIMED_OUT = 5,
            ROW_COL_MEMORY_ERROR = 6,
            ROW_COL_RUN_ERROR = 7,
            ROW_COL_TOTAL = 8,
            SIZE_PIT_MATRIX = 9;

     String[] COL_HEADING_0 = {"New commit", "Old commit"};
     String[] COL_HEADING_1 = {"N/E", "KLD", "SVD", "N/C", "N/V", "T/O", "M/E", "R/E", "Totals"};
     String[] ROW_HEADINGS = {
            "Old    N/E  ",
            "commit KLD  ",
            "       SVD  ",
            "       N/C  ",
            "       N/V  ",
            "       T/O  ",
            "       M/E  ",
            "       R/E  ",
          "\nNew commit\n" +
            "    Totals  "};

     String DIFF_STATUS_ADDED = "A";
     String DIFF_STATUS_DELETED = "D";
     String DIFF_STATUS_MODIFIED = "M";
     String DIFF_STATUS_COPIED = "C";
     String DIFF_STATUS_RENAMED = "R";

     String PIT_STATUS_KILLED = "KILLED";
     String PIT_STATUS_SURVIVED = "SURVIVED";
     String PIT_STATUS_NO_COVERAGE = "NO_COVERAGE";
     String PIT_STATUS_NON_VIABLE = "NON_VIABLE";
     String PIT_STATUS_TIMED_OUT = "TIMED_OUT";
     String PIT_STATUS_MEMORY_ERROR = "MEMORY_ERROR";
     String PIT_STATUS_RUN_ERROR = "RUN_ERROR";

     String STATUS_ADDED = "ADDED";
     String STATUS_CHANGED = "CHANGED";
     String STATUS_DELETED = "DELETED";
     String STATUS_DELETED_SHORT = "DEL";
     String STATUS_EXISTING = "EXISTING";
     String STATUS_NEW = "NEW";
     String STATUS_NON_EXISTENT = "N/E";
     String STATUS_REGRESSED = "REGRESSED";
     String STATUS_REMOVED = "REMOVED";
     String STATUS_UNCHANGED = "UNCHANGED";
     String STATUS_UNKNOWN = "UNKNOWN";

     String REGRESSION_NOTE = "NOTE! This mutation is no longer killed. " +
            "Potential regression introduced in commit " + HASH_PLACEHOLDER;
}