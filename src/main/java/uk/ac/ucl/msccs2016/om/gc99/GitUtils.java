package uk.ac.ucl.msccs2016.om.gc99;

import java.util.List;


class GitUtils implements Git {


    static boolean indexNotEmpty(String projectPath, CommandExecutor commandExecutor) {
        String gitOptions = GIT_OPTION_PATH + projectPath;
        String diffOptions = DIFF_OPTION_NAME_ONLY + DIFF_OPTION_CACHED;

        String command = buildGitDiffCommand(gitOptions, diffOptions, "", "", "", "");

        //commandExecutor.execute(command, true);
        commandExecutor.execute(command);

//        System.out.println("Standard output:\n" + String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println("Standard error:\n" + String.join("\n", commandExecutor.getStandardError()));

        return commandExecutor.getStandardOutput().size() > 0;
    }


    static boolean untrackedNotEmpty(String projectPath, CommandExecutor commandExecutor) {
        String gitOptions = GIT_OPTION_PATH + projectPath;
        String lsFilesOptions = LS_FILES_OTHERS_OPTION + LS_FILES_EXCLUDE_STANDARD_OPTION;

        String command = GIT_LS_FILES_COMMAND.replace(GIT_OPTIONS_PLACEHOLDER, gitOptions);
        command = command.replace("<lsFilesOptions>", lsFilesOptions);

        //commandExecutor.execute(command, true);
        commandExecutor.execute(command);

//        System.out.println("Standard output:\n" + String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println("Standard error:\n" + String.join("\n", commandExecutor.getStandardError()));

        return commandExecutor.getStandardOutput().size() > 0;
    }


    static String commitIndex(String projectPath, CommandExecutor commandExecutor){
        return commitIndex(COMMIT_MESSAGE_PITSTAT_INDEX, projectPath, commandExecutor);
    }


    static String commitUntracked(String projectPath, CommandExecutor commandExecutor){
        stageAllUntracked(projectPath, commandExecutor);
        return commitIndex(COMMIT_MESSAGE_PITSTAT_UNTRACKED, projectPath, commandExecutor);
    }


    static String commitIndex(String commitMessage, String projectPath, CommandExecutor commandExecutor) {
        String gitOptions = GIT_OPTION_PATH + projectPath;
        String commitOptions = COMMIT_MESSAGE_OPTION + commitMessage;

        String command = GIT_COMMIT_COMMAND.replace(GIT_OPTIONS_PLACEHOLDER, gitOptions);
        command = command.replace("<commitOptions>", commitOptions);

        //commandExecutor.execute(command, true);
        commandExecutor.execute(command);

//        System.out.println("Standard output:\n" + String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println("Standard error:\n" + String.join("\n", commandExecutor.getStandardError()));

        return getCommitHash(HEAD, projectPath, commandExecutor);
    }


    static void stageAllUntracked(String projectPath, CommandExecutor commandExecutor){
        String gitOptions = GIT_OPTION_PATH + projectPath;

        String command = GIT_ADD_COMMAND.replace(GIT_OPTIONS_PLACEHOLDER, gitOptions);
        command = command.replace("<addOptions>", ADD_OPTION_ALL);

        //commandExecutor.execute(command, true);
        commandExecutor.execute(command);

//        System.out.println("Standard output:\n" + String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println("Standard error:\n" + String.join("\n", commandExecutor.getStandardError()));
    }


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


    static void gitResetSoftTo(String commit, String projectPath, CommandExecutor commandExecutor) {
        gitResetTo(commit, RESET_SOFT_OPTION, projectPath, commandExecutor);
    }


    static void gitResetMixedTo(String commit, String projectPath, CommandExecutor commandExecutor) {
        gitResetTo(commit, RESET_MIXED_OPTION, projectPath, commandExecutor);
    }


    static void gitResetHardTo(String commit, String projectPath, CommandExecutor commandExecutor) {
        gitResetTo(commit, RESET_HARD_OPTION, projectPath, commandExecutor);
    }


    static  void gitResetTo(String commit, String resetOption, String projectPath, CommandExecutor commandExecutor){
        String gitOptions = GIT_OPTION_PATH + projectPath;

        String command = GIT_RESET_COMMAND.replace(GIT_OPTIONS_PLACEHOLDER, gitOptions);
        command = command.replace("<resetOptions>", resetOption);
        command = command + commit;

//        commandExecutor.execute(command, true);
        commandExecutor.execute(command);

//        System.out.println("Standard output:\n" + String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println("Standard error:\n" + String.join("\n", commandExecutor.getStandardError()));

    }


    static void checkoutOriginalBranch(String originalGitBranch, String projectPath, CommandExecutor commandExecutor) {
        gitCheckout(originalGitBranch, projectPath, commandExecutor);
    }


    static void gitCheckout(String target, String projectPath, CommandExecutor commandExecutor) {
        gitCheckout(target, "", projectPath, commandExecutor);
    }


    static void gitCheckout(String target, String checkoutOptions, String projectPath, CommandExecutor commandExecutor) {

        String gitOptions = GIT_OPTION_PATH + projectPath;

        String command = GIT_CHECKOUT_COMMAND.replace(GIT_OPTIONS_PLACEHOLDER, gitOptions);
        command = command.replace("<checkoutOptions>", checkoutOptions);
        command = command + target;

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