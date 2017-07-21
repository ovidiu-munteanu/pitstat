package uk.ac.ucl.msccs2016.om.gc99;

interface Git {

    String INDEX = "INDEX";
    String UNTRACKED = "UNTRACKED";
    String HEAD = "HEAD";
    String HEAD_PARENT = HEAD + "~";

    String INITIAL_COMMIT = "initial-commit";
    String MAX_VALUE = "max";

    String GIT = "git ";
    String GIT_OPTIONS_PLACEHOLDER = "<gitOptions>";

    String GIT_COMMAND = GIT + GIT_OPTIONS_PLACEHOLDER;

    String GIT_OPTION_NO_PAGER = " --no-pager ";
    String GIT_OPTION_PATH = " -C ";

    String GIT_LS_FILES_COMMAND = GIT_COMMAND + " ls-files <lsFilesOptions> ";
    String LS_FILES_OTHERS_OPTION = " --others ";
    String LS_FILES_EXCLUDE_STANDARD_OPTION = " --exclude-standard ";

    String GIT_REV_LIST_COMMAND = GIT_COMMAND + " rev-list <revListOptions> ";
    String REV_LIST_ALL_OPTION = " --all ";

    String GIT_REV_PARSE_COMMAND = GIT_COMMAND + " rev-parse <revParseOptions> ";
    String REV_PARSE_OPTION_ABBREV_REF = " --abbrev-ref ";

    String GIT_CHECKOUT_COMMAND = GIT_COMMAND + " checkout <checkoutOptions> ";
    String CHECKOUT_OPTION_NEW_BRANCH = " -b ";

    String GIT_BRANCH_COMMAND = GIT_COMMAND + " branch <branchOptions> ";
    String BRANCH_DELETE_OPTION = " -D ";
    String BRANCH_FORCE_OPTION = " -f ";

    String GIT_RESET_COMMAND = GIT_COMMAND + " reset <resetOptions> ";
    String RESET_SOFT_OPTION = " --soft ";
    String RESET_MIXED_OPTION = " --mixed ";
    String RESET_HARD_OPTION = " --hard ";

    String GIT_DIFF_COMMAND = GIT_COMMAND + " diff <diffOptions> <oldCommit> <oldFile> <newCommit> <newFileName>";
    String DIFF_OPTION_NAME_STATUS = " --name-status ";
    String DIFF_OPTION_NAME_ONLY = " --name-only ";
    String DIFF_OPTION_CACHED = " --cached ";
    String DIFF_OPTION_NO_CONTEXT = " -U0 ";
    String DIFF_OPTION_FIND_COPIES = " -C ";
    String DIFF_OPTION_FIND_COPIES_HARDER = " -C -C ";

    String GIT_ADD_COMMAND = GIT_COMMAND + " add <addOptions> ";
    String ADD_OPTION_ALL = " -A ";

    String GIT_COMMIT_COMMAND = GIT_COMMAND + " commit <commitOptions> ";
    String COMMIT_MESSAGE_OPTION = " -m ";
    String COMMIT_MESSAGE_PITSTAT_INDEX = "temp_commit_pitstat_index";
    String COMMIT_MESSAGE_PITSTAT_UNTRACKED = "temp_commit_pitstat_untracked";
}