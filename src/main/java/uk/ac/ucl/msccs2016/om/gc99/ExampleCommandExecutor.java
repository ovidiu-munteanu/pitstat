package uk.ac.ucl.msccs2016.om.gc99;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class ExampleCommandExecutor {

    public static void main(String[] args) throws Exception {
        new ExampleCommandExecutor();
    }

    private ExampleCommandExecutor() throws IOException, InterruptedException {

        List<String> commands = new ArrayList<>();
        commands.add("git");
        commands.add("-C");
        commands.add("\"D:/X/github/joda-time-pit\"");
        commands.add("status");
        commands.add("");


        // execute the command
        CommandExecutor commandExecutor = new CommandExecutor();
        commandExecutor.setCommand(commands);
        int result = commandExecutor.executeCommand();


        // get the stdout and stderr from the command that was run
        StringBuilder stdout = commandExecutor.getStandardOutput();
        StringBuilder stderr = commandExecutor.getStandardError();

        // print the stdout and stderr
        System.out.println("The numeric result of the command was: " + result + "\n");
        System.out.println("STDOUT:");
        System.out.println(stdout);
        System.out.println("STDERR:");
        System.out.println(stderr);
    }
}
