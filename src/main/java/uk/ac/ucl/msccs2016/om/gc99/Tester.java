package uk.ac.ucl.msccs2016.om.gc99;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class Tester {

    public static void main(String[] args) throws Exception {

        DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = documentBuilder.parse(new File("test.xml"));

        Element project = (Element) doc.getElementsByTagName("project").item(0);
        Element build = (Element) project.getElementsByTagName("build").item(0);
        Element plugins = (Element) build.getElementsByTagName("plugins").item(0);


        NodeList pluginsList = plugins.getElementsByTagName("plugin");

        boolean pitPluginNotFound = true;

        for (int i = 0; i < pluginsList.getLength(); i++) {
            Node plugin = pluginsList.item(i);

            String groupId = ((Element) plugin).getElementsByTagName("groupId").item(0).getTextContent();

            if (groupId.equals("org.pitest")) {
                Node parentNode = pluginsList.item(i).getParentNode();

                parentNode.removeChild(plugin);
                parentNode.appendChild(pitPlugin(doc));

                pitPluginNotFound = false;

                break;
            }

        }

        if (pitPluginNotFound) plugins.appendChild(pitPlugin(doc));

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(new DOMSource(doc), new StreamResult(new File("test_1.xml")));

        Path path = Files.createTempFile("test",".xml");

        File file = path.toFile();

        file.deleteOnExit();;

        System.out.println(path);

        System.out.println("Done");


    }

    private static Element pitPlugin(Document doc) {
        Element pitPlugin = doc.createElement("plugin");

        Element pitGroupId = doc.createElement("groupId");
        Element pitArtifactId = doc.createElement("artifactId");
        Element pitVersion = doc.createElement("version");

        Element pitConfiguration = doc.createElement("configuration");

        Element pitTimestampedReports = doc.createElement("timestampedReports");
        Element pitOutputFormats = doc.createElement("outputFormats");
        Element pitThreads = doc.createElement("threads");

        pitGroupId.appendChild(doc.createTextNode("org.pitest"));
        pitArtifactId.appendChild(doc.createTextNode("pitest-maven"));
        pitVersion.appendChild(doc.createTextNode("1.2.0"));

        pitTimestampedReports.appendChild(doc.createTextNode("false"));
        pitOutputFormats.appendChild(doc.createTextNode("XML"));
        pitThreads.appendChild(doc.createTextNode("4"));

        pitConfiguration.appendChild(pitTimestampedReports);
        pitConfiguration.appendChild(pitOutputFormats);
        pitConfiguration.appendChild(pitThreads);

        pitPlugin.appendChild(pitGroupId);
        pitPlugin.appendChild(pitArtifactId);
        pitPlugin.appendChild(pitVersion);
        pitPlugin.appendChild(pitConfiguration);

        return pitPlugin;
    }


}