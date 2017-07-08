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


    String pomFile = "pom.xml";

    String mavenJavaMainSrcPath = "src/main/java";

    String pitReportsPath = "target/pit-reports";
    String pitMutationsFile = "mutations.xml";

    String diffOutputBaseFileName = "diffOutput-<date-hash>.json";
    String pitOutputBaseFileName = "pitOutput-<date-hash>.json";


    String mvnGoalTest = " test ";
    String mvnGoalPitest = " org.pitest:pitest-maven:mutationCoverage ";


    String headCommit = " HEAD ";
    String parentOfHeadCommit = " HEAD~ ";


    String gitOptionsPlaceholder = "<gitOptions>";

    String gitRevListCommand = "git <gitOptions> rev-list <revListOptions> ";
    String revListAllOption = " --all ";

    String gitRevParseCommand = "git <gitOptions> rev-parse <revParseOptions> ";
    String revParseOptionAbbrevRef = " --abbrev-ref ";

    String gitCheckoutCommand = "git <gitOptions> checkout <checkoutOptions> ";
    String checkoutOptionNewBranch = " -b ";

    String gitBranchCommand = "git <gitOptions> branch <branchOptions> ";
    String branchDeleteOption = " -D ";
    String branchForceOption = " -f ";

    String gitResetCommand  ="git <gitOptions> reset <resetOptions> ";
    String resetHardOption =  " --hard ";

    String gitDiffCommand = "git <gitOptions> diff <diffOptions> <oldCommit> <oldFile> <newCommit> <newFileName>";

    String gitOptionNoPager = " --no-pager ";
    String gitOptionPath = " -C ";

    String diffOptionNameStatus = " --name-status ";
    String diffOptionNoContext = " -U0 ";
    String diffOptionFindCopies = " -C ";
    String diffOptionFindCopiesHarder = " -C -C ";

}
