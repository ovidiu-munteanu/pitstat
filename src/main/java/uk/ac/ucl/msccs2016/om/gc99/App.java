package uk.ac.ucl.msccs2016.om.gc99;

public class App {

    public static void main(String[] args) throws Exception {

        MainWorker worker = new MainWorker(
                "D:/X/projIdea/MavenTest",
//                "D:/X/github/commons-collections",
//                "D:/X/github/joda-time",
                "HEAD~",
                "HEAD",
                "target/pit-reports",
                true);

        worker.doWork();

    }

}
