package uk.ac.ucl.msccs2016.om.gc99;

public class Tester {

    public static void main(String[] args) throws Exception {


    }

}


//    private Path getLatestPitReportPath(String pitReportPath, boolean pitReportPathRelative) {
//
//        Path latestPitReportPath = Paths.get((pitReportPathRelative ? projectPath : ""), pitReportPath);
//
//        try {
//            latestPitReportPath = Files.list(latestPitReportPath).filter(Files::isDirectory).max(Comparator.naturalOrder()).get();
//        } catch (IOException e) {
//            e.printStackTrace();
//        } catch (NoSuchElementException e) {
//            e.printStackTrace();
//        }
//
//        System.out.println("\nLatest Pit Report Path: " + latestPitReportPath + "\n");
//
//        return latestPitReportPath;
//    }



