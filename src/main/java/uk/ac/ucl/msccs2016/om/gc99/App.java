package uk.ac.ucl.msccs2016.om.gc99;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

public class App {

    private final String repoPath = "D:/X/_projects/idea/MavenTest";

    private final String gitCommand = "git <gitOptions> diff <diffOptions> HEAD~ HEAD";

    private final String gitOptionPath = " -C ";

    private final String diffOptionNameStatus = " --name-status ";
    private final String diffOptionNoContext = " -U0 ";

    private final String diffOptionFilter = " --diff-filter=";
    private final String diffFilterM = "M";


    private CommandExecutor commandExecutor;
    private List<String> modifiedFilesList;


    public static void main(String[] args) throws Exception {
        App app = new App();

        String command = app.gitCommand;

        command = command.replace("<gitOptions>", app.gitOptionPath + app.wrapInvCommas(app.repoPath));
        command = command.replace("<diffOptions>", app.diffOptionNameStatus + app.diffOptionFilter + app.diffFilterM);

        app.commandExecutor.executeCommand(command, true);

        String commandStandardOutput = app.commandExecutor.getStandardOutput().toString();
        String commandStandardError = app.commandExecutor.getStandardError().toString();

        int commandStandardOutputLines = app.commandExecutor.getStandardOutputLines();
        int commandStandardErrorLines = app.commandExecutor.getStandardErrorLines();


        //System.out.println("$ " + command);
        System.out.println(commandStandardOutput);


        String modifiedFiles = commandStandardOutput.replaceAll("M\\s+", "");

        app.modifiedFilesList = Arrays.asList(modifiedFiles.split("\\n"));

        int index = 0;

        for (String fileName : app.modifiedFilesList) {
            System.out.println("\nModified file " + ++index + ": " + fileName);

            List<String> mapFileLines = Files.readAllLines(Paths.get(app.repoPath, fileName), StandardCharsets.UTF_8);
            int mapFilePointer = 1;

            // Calculate number of characters to use in formatting of line number based on number of lines in file
            // i.e. number of characters = 1 + digits in number of lines
            int digitsNo = 1 + Integer.toString(mapFileLines.size()).length();
            String format = "%-" + digitsNo + "d";

            command = app.gitCommand;
            command = command.replace("<gitOptions>", app.gitOptionPath + ' ' + app.wrapInvCommas(app.repoPath));
            command = command.replace("<diffOptions>", app.diffOptionNoContext);
            command = command + " -- " + app.wrapInvCommas(fileName);

            app.commandExecutor.executeCommand(command, true);

            String diffOutput = app.commandExecutor.getStandardOutput().toString();

            List<String> diffOutputLines = Arrays.asList(diffOutput.split("\n"));

            ListIterator<String> diffOutputIterator = diffOutputLines.listIterator();

            // Skip git diff header lines, i.e. skip until the first line starting with @@ is found
            while (diffOutputIterator.hasNext() && !diffOutputIterator.next().startsWith("@@")) ;
            diffOutputIterator.previous();

            int diffOldPointer, diffOldLinesNo, diffNewPointer, diffNewLinesNo, lineOffset = 0;

            int oldFilePointer = 1, newFilePointer = 1;

            while (diffOutputIterator.hasNext()) {

                String diffLine = diffOutputIterator.next();

                if (diffLine.startsWith("@@")) {

                    // add ",1" in git diff @@ lines where the number of lines is omitted by default
                    // eg.  @@ -23 +23 @@       becomes     @@ -23,1 + 23,1 @@
                    //      @@ -23 +22,0 @@     becomes     @@ 23,1 +22,0 @@
                    // etc.
                    diffLine = diffLine.substring(3, diffLine.lastIndexOf("@@") - 1);
                    if (diffLine.lastIndexOf(",") < diffLine.indexOf("+"))
                        diffLine = diffLine + ",1";
                    if (diffLine.indexOf(",") == diffLine.lastIndexOf(","))
                        diffLine = new StringBuilder(diffLine).insert(diffLine.indexOf("+") - 1, ",1").toString();

                    diffLine = diffLine.replace("-", "");
                    diffLine = diffLine.replace("+", "");
                    String split[] = diffLine.split(",|\\s");

                    diffOldPointer = Integer.valueOf(split[0]);
                    diffOldLinesNo = Integer.valueOf(split[1]);
                    diffNewPointer = Integer.valueOf(split[2]);
                    diffNewLinesNo = Integer.valueOf(split[3]);

                    int actualNewPointer = (diffNewLinesNo == 0 ? diffNewPointer + 1 : diffNewPointer) + lineOffset;

                    if (mapFilePointer < actualNewPointer) {

                        int lineNo = mapFilePointer;

                        for (; lineNo < actualNewPointer; lineNo++) {

                            String numberedLine = String.format(format, oldFilePointer) + ":  " +
                                    String.format(format, newFilePointer) + ":  " + mapFileLines.get(lineNo - 1);

                            mapFileLines.set(lineNo - 1, numberedLine);

                            oldFilePointer++;
                            newFilePointer++;
                        }
                        mapFilePointer = lineNo;

                    }

                } else if (diffLine.startsWith("-")) {

                    diffLine = "\u001B[31m" + String.format(format, oldFilePointer) + ":  " + "DEL :  " + diffLine.substring(1) + "\u001B[0m";

                    mapFileLines.add(mapFilePointer - 1, diffLine);

                    mapFilePointer++;
                    oldFilePointer++;
                    lineOffset++;

                } else {

                    diffLine = "\u001B[32m" + "NEW :  " + String.format(format, newFilePointer) + ":  " + diffLine.substring(1) + "\u001B[0m";

                    mapFileLines.set(mapFilePointer - 1, diffLine);

                    mapFilePointer++;
                    newFilePointer++;


                }
            }

            ListIterator<String> mapFileIterator = mapFileLines.listIterator(mapFilePointer - 1);
            while (mapFileIterator.hasNext()) {
                String mapFileLine = mapFileIterator.next();
                mapFileLine = String.format(format, oldFilePointer) + ":  " +
                        String.format(format, newFilePointer) + ":  " + mapFileLine;
                mapFileIterator.set(mapFileLine);

                oldFilePointer++;
                newFilePointer++;
            }


//            System.out.println(String.join("\n", diffOutputLines));


//            mapFileIterator = mapFileLines.listIterator();
//            int lineNo = 0;
//            while (mapFileIterator.hasNext()) {
//                String mapFileLine = mapFileIterator.next();
//                mapFileIterator.set(String.format(format, ++lineNo) + ":  " + mapFileLine);
//            }

            System.out.println(String.join("\n", mapFileLines));


        }


        System.out.println("\nModified files: " + commandStandardOutputLines);

    }

    /**
     * App constructor
     */
    private App() {
        commandExecutor = new CommandExecutor();
    }


    String wrapInvCommas(String s) {
        return "\"" + s + "\"";
    }


}
