package uk.ac.ucl.msccs2016.om.gc99;

import org.apache.commons.lang3.SystemUtils;

import java.util.ArrayList;


public class ExampleSimpleShellCommand {


    public static void main(String[] args) {

        ExampleSimpleShellCommand obj = new ExampleSimpleShellCommand();


        ArrayList<String> command = new ArrayList<>();

//        command.add("git");
//        command.add("-C");
//        command.add("\"D:/X/github/joda-time-pit\"");
//        command.add("diff");
//        command.add("HEAD~");


        String mavenCommand = SystemUtils.IS_OS_WINDOWS ? "mvn.cmd" : "mvn";

        command.add(mavenCommand);
        command.add("-f");
        command.add("\"D:/X/github/joda-time-pit\"");
        command.add("clean");

        obj.executeCommand(command);

    }

    private void executeCommand(ArrayList<String> command) {


        try {

            ProcessBuilder builder = new ProcessBuilder(command);

            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);

            Process p = builder.start();



            p.waitFor();

        } catch (Exception e) {
            e.printStackTrace();
        }



    }

}