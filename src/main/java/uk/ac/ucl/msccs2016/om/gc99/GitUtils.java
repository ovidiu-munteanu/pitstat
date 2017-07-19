package uk.ac.ucl.msccs2016.om.gc99;

import java.util.List;


@SuppressWarnings("Duplicates")
class GitUtils {

    private static final String GIT_OPTIONS_PLACEHOLDER = "<gitOptions>";

    private static final String GIT_REV_LIST_COMMAND = "git <gitOptions> rev-list <revListOptions> ";
    private static final String REV_LIST_ALL_OPTION = " --all ";

    private static final String GIT_REV_PARSE_COMMAND = "git <gitOptions> rev-parse <revParseOptions> ";
    private static final String REV_PARSE_OPTION_ABBREV_REF = " --abbrev-ref ";

    private static final String GIT_CHECKOUT_COMMAND = "git <gitOptions> checkout <checkoutOptions> ";
    private static final String CHECKOUT_OPTION_NEW_BRANCH = " -b ";

    private static final String GIT_BRANCH_COMMAND = "git <gitOptions> branch <branchOptions> ";
    private static final String BRANCH_DELETE_OPTION = " -D ";
    private static final String BRANCH_FORCE_OPTION = " -f ";

    private static final String GIT_RESET_COMMAND = "git <gitOptions> reset <resetOptions> ";
    private static final String RESET_HARD_OPTION = " --hard ";

    private static final String GIT_DIFF_COMMAND = "git <gitOptions> diff <diffOptions> <oldCommit> <oldFile> <newCommit> <newFileName>";

    private static final String GIT_OPTION_NO_PAGER = " --no-pager ";
    private static final String GIT_OPTION_PATH = " -C ";

    private static final String DIFF_OPTION_NAME_STATUS = " --name-status ";
    private static final String DIFF_OPTION_NO_CONTEXT = " -U0 ";
    private static final String DIFF_OPTION_FIND_COPIES = " -C ";
    private static final String DIFF_OPTION_FIND_COPIES_HARDER = " -C -C ";


    static String parseCommit(String commit, String originalGitBranch, List<String> commitsHashList,
                              String projectPath, CommandExecutor commandExecutor) {

        if (commit.equals("") || commit.equals("HEAD") || commit.equals(originalGitBranch))
            return getCommitHash(commit, projectPath, commandExecutor);

        if (commit.startsWith("HEAD") || commit.startsWith(originalGitBranch)) {

            String tail = commit.substring(4);

            if (tail.equals("~")) return getCommitHash(commit, projectPath, commandExecutor);

            if (!tail.startsWith("~")) {

                System.out.println("The revision you specified is invalid.");
                System.out.println("Tip: enter the revision in form <refname>~<n>");
                App.systemExit(99);

            } else {

                String generationString = tail.substring(1);
                int generation = 0;

                try {
                    generation = Integer.valueOf(generationString);
                    if (generation < 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    System.out.println("The generation you specified is invalid: " + generationString);
                    System.out.println("Tip: the generation should be a positive integer");
                    App.systemExit(99);
                }

                if (generation > (commitsHashList.size() - 1)) {
                    System.out.println("The generation you specified exceeds the history of the branch.");
                    System.out.println("Tip: this branch has " + (commitsHashList.size() - 1) + " past commits");
                    App.systemExit(99);
                }
            }

            return getCommitHash(commit, projectPath, commandExecutor);
        }

        if (commit.length() == 7 && shortRevExists(commit, commitsHashList))
            return getCommitHash(commit, projectPath, commandExecutor);

        if (commit.length() == 40 && commitsHashList.contains(commit)) return commit;

        return null;
    }


    static boolean shortRevExists(String shortRev, List<String> commitsHashList) {
        for (String commit : commitsHashList)
            if (commit.startsWith(shortRev)) return true;
        return false;
    }


    static String getGitBranch(String commit, String projectPath, CommandExecutor commandExecutor) {

        String gitOptions = GIT_OPTION_PATH + projectPath;

        String command = GIT_REV_PARSE_COMMAND.replace(GIT_OPTIONS_PLACEHOLDER, gitOptions);
        command = command.replace("<revParseOptions>", REV_PARSE_OPTION_ABBREV_REF);
        command = command + commit;


        //commandExecutor.execute(command, true);
        commandExecutor.execute(command);

//        System.out.println("Standard output:\n" + String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println("Standard error:\n" + String.join("\n", commandExecutor.getStandardError()));

        return String.join("", commandExecutor.getStandardOutput());
    }


    static String checkoutPitStatBranch(String startTime, String projectPath, CommandExecutor commandExecutor) {

        String pitStatBranch = "pitstat" + startTime;

        String gitOptions = GIT_OPTION_PATH + projectPath;

        String command = GIT_CHECKOUT_COMMAND.replace(GIT_OPTIONS_PLACEHOLDER, gitOptions);
        command = command.replace("<checkoutOptions>", CHECKOUT_OPTION_NEW_BRANCH);
        command = command + pitStatBranch;

        //commandExecutor.execute(command, true);
        commandExecutor.execute(command);

//        System.out.println("Standard output:\n" + String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println("Standard error:\n" + String.join("\n", commandExecutor.getStandardError()));

        return pitStatBranch;
    }


    static void checkoutOriginalBranch(String originalGitBranch, String projectPath, CommandExecutor commandExecutor) {
        String gitOptions = GIT_OPTION_PATH + projectPath;

        String command = GIT_CHECKOUT_COMMAND.replace(GIT_OPTIONS_PLACEHOLDER, gitOptions);
        command = command.replace("<checkoutOptions>", "");
        command = command + originalGitBranch;

//        commandExecutor.execute(command, true);
        commandExecutor.execute(command);

//        System.out.println("Standard output:\n" + String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println("Standard error:\n" + String.join("\n", commandExecutor.getStandardError()));
    }


    static void deletePitStatBranch(String pitStatBranch, String projectPath, CommandExecutor commandExecutor) {
        String gitOptions = GIT_OPTION_PATH + projectPath;

        String command = GIT_BRANCH_COMMAND.replace(GIT_OPTIONS_PLACEHOLDER, gitOptions);
        command = command.replace("<branchOptions>", BRANCH_DELETE_OPTION + BRANCH_FORCE_OPTION);
        command = command + pitStatBranch;

//        commandExecutor.execute(command, true);
        commandExecutor.execute(command);

//        System.out.println("Standard output:\n" + String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println("Standard error:\n" + String.join("\n", commandExecutor.getStandardError()));
    }


    static void rollBackTo(String commit, String projectPath, CommandExecutor commandExecutor) {

        String gitOptions = GIT_OPTION_PATH + projectPath;

        String command = GIT_RESET_COMMAND.replace(GIT_OPTIONS_PLACEHOLDER, gitOptions);
        command = command.replace("<resetOptions>", RESET_HARD_OPTION);
        command = command + commit;

//        commandExecutor.execute(command, true);
        commandExecutor.execute(command);

//        System.out.println("Standard output:\n" + String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println("Standard error:\n" + String.join("\n", commandExecutor.getStandardError()));
    }


    static List<String> getCommitsHashList(String projectPath, CommandExecutor commandExecutor) {
        String gitOptions = GIT_OPTION_NO_PAGER + GIT_OPTION_PATH + projectPath;

        String command = GIT_REV_LIST_COMMAND.replace(GIT_OPTIONS_PLACEHOLDER, gitOptions);
        command = command.replace("<revListOptions>", REV_LIST_ALL_OPTION);

//        commandExecutor.execute(command, true);
        commandExecutor.execute(command);

//        System.out.println("Standard output:\n" + String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println("Standard error:\n" + String.join("\n", commandExecutor.getStandardError()));

        return commandExecutor.getStandardOutput();
    }


    static String getCommitHash(String commit, String projectPath, CommandExecutor commandExecutor) {

        if (commit.length() == 0) return "";

        String gitOptions = GIT_OPTION_PATH + projectPath;

        String command = GIT_REV_PARSE_COMMAND.replace(GIT_OPTIONS_PLACEHOLDER, gitOptions);
        command = command.replace("<revParseOptions>", "");
        command = command + commit;

//        commandExecutor.execute(command, true);
        commandExecutor.execute(command);

//        System.out.println("Standard output:\n" + String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println("Standard error:\n" + String.join("\n", commandExecutor.getStandardError()));

        return commandExecutor.getStandardOutput().get(0);
    }


    static List<String> gitDiffNameStatus(String currentCommitHash, String parentCommitHash,
                                          String projectPath, CommandExecutor commandExecutor) {

        String gitOptions = GIT_OPTION_NO_PAGER + GIT_OPTION_PATH + projectPath;
        String diffOptions = DIFF_OPTION_NAME_STATUS + DIFF_OPTION_FIND_COPIES_HARDER;
        String gitOldFile = "", gitNewFile = "";

        String command = buildGitDiffCommand(
                gitOptions,
                diffOptions,
                parentCommitHash,
                gitOldFile,
                currentCommitHash,
                gitNewFile
        );

//        commandExecutor.execute(command, true);
        commandExecutor.execute(command);

//        System.out.println("Standard output:\n" + String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println("Standard error:\n" + String.join("\n", commandExecutor.getStandardError()));

        return commandExecutor.getStandardOutput();
    }

    static List<String> gitDiff(String changedFile, String newFile,
                                String currentCommitHash, String parentCommitHash,
                                String projectPath, CommandExecutor commandExecutor) {

        String gitOptions = GIT_OPTION_NO_PAGER + GIT_OPTION_PATH + projectPath;
        String diffOptions = DIFF_OPTION_FIND_COPIES_HARDER + DIFF_OPTION_NO_CONTEXT;
        String gitOldFile = " -- " + changedFile;
        String gitNewFile = " -- " + newFile;

        String command = buildGitDiffCommand(
                gitOptions, diffOptions,
                parentCommitHash, currentCommitHash,
                gitOldFile, gitNewFile);

        //commandExecutor.execute(command, true);
        commandExecutor.execute(command);

//        System.out.println("Standard output:\n" + String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println("Standard error:\n" + String.join("\n", commandExecutor.getStandardError()));

        return commandExecutor.getStandardOutput();
    }

    static String buildGitDiffCommand(String gitOptions, String diffOptions, String oldCommit,
                                      String newCommit, String gitOldFile, String gitNewFile) {

        String command = GIT_DIFF_COMMAND;

        command = command.replace(GIT_OPTIONS_PLACEHOLDER, gitOptions);
        command = command.replace("<diffOptions>", diffOptions);
        command = command.replace("<oldCommit>", oldCommit);
        command = command.replace("<oldFile>", gitOldFile);
        command = command.replace("<newCommit>", newCommit);
        command = command.replace("<newFileName>", gitNewFile);

        return command;
    }
}