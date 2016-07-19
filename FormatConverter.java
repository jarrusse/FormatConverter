import org.w3c.dom.*;
import org.json.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.io.*;


/**
 * Created by jarrusse on 6/17/2016.
 */
public class FormatConverter {

    //private String jsonString;
    private DocumentBuilderFactory docFactory;
    private DocumentBuilder docBuilder;
    private Document doc;

    public FormatConverter(){
        docFactory = DocumentBuilderFactory.newInstance();
        try {
            docBuilder = docFactory.newDocumentBuilder();
        }catch(ParserConfigurationException pce){
            System.out.print(pce);
        }
        doc = docBuilder.newDocument();

    }

    private String readJsonFromFile(String inputFile){
        FileInputStream in = null;

        String jsonString = null;

        try {
            byte[] encoded = Files.readAllBytes(Paths.get(inputFile));
            jsonString = new String(encoded, StandardCharsets.UTF_8);
        }catch(Exception e){
            System.out.print(e.getMessage());
        }

        return jsonString;
    }

    private String getCommaSeparatedLocation(JSONObject position){

        String xPosition = Integer.toString(position.getInt("x"));
        String yPosition = Integer.toString(position.getInt("y"));
        return xPosition + "," + yPosition;
    }

    private void jsonParser(String jsonString){

        JSONObject obj = new JSONObject(jsonString);
        JSONArray cells = obj.getJSONArray("cells");

        ArrayList<Link> links = new ArrayList<>();
        ArrayList<Node> physicalRouters  = new ArrayList<>();
        ArrayList<Node> clouds = new ArrayList<>();
        ArrayList<Node> physicalSwitches = new ArrayList<>();
        ArrayList<Node> deviceSwitches = new ArrayList<>();
        ArrayList<Node> containers = new ArrayList<>();

        for(Object objectCell : cells){

            JSONObject jsonObjectCell = (JSONObject) objectCell;
            String cellType = jsonObjectCell.getString("type");

            if(cellType.equals("cisco.Device")){

                String deviceType = jsonObjectCell.getJSONObject("properties").getString("name");

                if(deviceType.equals("CLOUD")){

                    String name = "cloud" +  String.valueOf(clouds.size() + 1);
                    String id = jsonObjectCell.getString("id");
                    String location = getCommaSeparatedLocation(jsonObjectCell.getJSONObject("position"));

                    clouds.add(new Node(id,name,location));
                }
                else if(deviceType.equalsIgnoreCase("Switch")){
                    //get type from properties

                    String name = "switch" +  String.valueOf(deviceSwitches.size() + 1);
                    String id = jsonObjectCell.getString("id");
                    String location = getCommaSeparatedLocation(jsonObjectCell.getJSONObject("position"));


                    deviceSwitches.add(new Node(id,name,location));
                }
                else{
                    System.out.println("Found unknown device type in cisco.Device: " + deviceType);
                }
            }
            else if(cellType.equals("cisco.Physical")){

                String deviceType = jsonObjectCell.getJSONObject("properties").getString("name");

                if(deviceType.equals("Router")) {

                    String id = jsonObjectCell.getString("id");
                    String name = "router" +  String.valueOf(physicalRouters.size() + 1);
                    String location = getCommaSeparatedLocation(jsonObjectCell.getJSONObject("position"));

                    physicalRouters.add(new Node(id,name,location));
                }
                else if(deviceType.equalsIgnoreCase("switch")){
                    //get type from properties file

                    String name = "switch" +  String.valueOf(physicalSwitches.size() + 1);
                    String id = jsonObjectCell.getString("id");
                    String location = getCommaSeparatedLocation(jsonObjectCell.getJSONObject("position"));

                    physicalSwitches.add(new Node(id,name,location));
                }
                else{
                    System.out.println("Found unknown device type in cisco.Physical: " + deviceType);
                }
            }
            else if(cellType.equals("cisco.Container")){

                String id = jsonObjectCell.getString("id");
                String name = jsonObjectCell.getJSONObject("properties").getString("name") +  String.valueOf(deviceSwitches.size() + 1);
                String location = getCommaSeparatedLocation(jsonObjectCell.getJSONObject("position"));

                containers.add(new Node(id,name,location));
            }
            else if(cellType.equals("cisco.Virtual")) {
                String parent = jsonObjectCell.getString("parent");
                for (Node container : containers) {
                    //If this isnt working switch it to comparing name instead
                    if (container.getId().equals(parent)) {
                        container.addDevice(jsonObjectCell.getJSONObject("properties").getString("name"));
                    }
                }
            }
            else if(cellType.equals("link")){
                Boolean isLinkVirtual = false;

                if(jsonObjectCell.has("parent")){
                    if(jsonObjectCell.getString("parent").equals("UCS") || jsonObjectCell.getString("parent").equals("UCS-E")){
                        isLinkVirtual = true;
                    }
                }

                //UCS-E may be incorrect
                if(!isLinkVirtual) {
                    String sourceID = jsonObjectCell.getJSONObject("source").getString("id");
                    JSONObject target = jsonObjectCell.getJSONObject("target");
                    String targetID = target.getString("id");
                    //String targetPort = target.getString("port");
                    //String targetSelector = target.getString("selector");
                    //String targetType = target.getString("network");

                    Link link = new Link();

                    link.setSourceID(sourceID);
                    link.setTargetID(targetID);

                    links.add(link);
                }
            }
            else if(cellType.equals("cisco.Text")){
                //We do not need to do anything with the text
            }
            else{
                System.out.println("Found unknown type: " + cellType);
            }
        }

        writeXMLtoFile(links,physicalRouters,clouds,physicalSwitches,deviceSwitches,containers);
    }

    private void writeXMLtoFile(ArrayList<Link> links, ArrayList<Node> physicalRouters, ArrayList<Node> clouds,
                                ArrayList<Node> physicalSwitches, ArrayList<Node> deviceSwitches,ArrayList<Node> containers){

        Element topology = doc.createElement("topology");
        //Change these to read from a properties file

        topology.setAttribute("xmlns","http://www.cisco.com/VIRL");
        topology.setAttribute("xmlns:xsi","http://www.w3.org/2001/XMLSchema-instance");
        topology.setAttribute("schemaVersion","0.6");
        topology.setAttribute("xsi:schemaLocation","http://www.cisco.com/VIRL http://cide.cisco.com/vmmaestro/schema/virl.xsd");

        doc.appendChild(topology);

        ArrayList<Element> nodes = new ArrayList<>();
        ArrayList<Element> connections = new ArrayList<>();

        //All of these need to be given vm images!

        //Add the nodes
        for(Node router : physicalRouters)
        {
            Element physicalRouterNode = doc.createElement("node");

            physicalRouterNode.setAttribute("name",router.getName());
            physicalRouterNode.setAttribute("id",router.getId());
            physicalRouterNode.setAttribute("type","SIMPLE");
            //physicalRouterNode.setAttribute("subtype",router.getSubtype());
            physicalRouterNode.setAttribute("location",router.getLocation());
            physicalRouterNode.setAttribute("subtype","vios");
            //Change to reading from properties file
            physicalRouterNode.setAttribute("vmImage","/usr/share/vmcloud/data/images/vios.ova");

            nodes.add(physicalRouterNode);

        }

        for(Node cloud : clouds){

            Element cloudNode = doc.createElement("node");

            cloudNode.setAttribute("name", cloud.getName());
            cloudNode.setAttribute("id", cloud.getId());
            cloudNode.setAttribute("location",cloud.getLocation());
            cloudNode.setAttribute("type","SIMPLE");
            cloudNode.setAttribute("subtype","vios");
            //This may be the incorrect ova
            cloudNode.setAttribute("vmImage","/usr/share/vmcloud/data/images/vios.ova");

            nodes.add(cloudNode);

        }

        for(Node physicalSwitch : physicalSwitches){

            Element physicalSwitchNode = doc.createElement("node");

            physicalSwitchNode.setAttribute("name",physicalSwitch.getName());
            physicalSwitchNode.setAttribute("id",physicalSwitch.getId());
            physicalSwitchNode.setAttribute("location",physicalSwitch.getLocation());
            physicalSwitchNode.setAttribute("type","SIMPLE");
            physicalSwitchNode.setAttribute("subtype","vios");
            //This may be the incorrect ova
            physicalSwitchNode.setAttribute("vmImage","/usr/share/vmcloud/data/images/vios.ova");

            nodes.add(physicalSwitchNode);

        }

        for(Node deviceSwitch : deviceSwitches){

            Element deviceSwitchNode = doc.createElement("node");

            deviceSwitchNode.setAttribute("name","Switch");
            deviceSwitchNode.setAttribute("id",deviceSwitch.getId());
            deviceSwitchNode.setAttribute("location",deviceSwitch.getLocation());


            nodes.add(deviceSwitchNode);
        }

        for(Node container : containers){

            Element containerNode = doc.createElement("node");

            ArrayList<String> devices = container.getDevices();
            ArrayList<String> deviceOvaFilePaths = new ArrayList<>();

            for(String device : devices){

                //This should be removed later
                String tempPath = "/usr/share/vmcloud/data/images/vios.ova";

                //These should be added to a properties file as well
                if(device.equals("Router")){
                    deviceOvaFilePaths.add(tempPath);
                }
                else if(device.equals("Firewall")){
                    deviceOvaFilePaths.add(tempPath);
                }
                else if(device.equals("Switch")){
                    deviceOvaFilePaths.add("/usr/share/vmcloud/data/images/n9k.ova");
                }
                else if(device.equals("WAAS")){
                    deviceOvaFilePaths.add(tempPath);
                }
                else if(device.equals("WLC")){
                    deviceOvaFilePaths.add(tempPath);
                }
                else{
                    System.out.println("Found unknown virtual device: " + device);
                }
            }

            String spaceSeparatedFilePaths = "";

            for(String filePath : deviceOvaFilePaths) {
                spaceSeparatedFilePaths = spaceSeparatedFilePaths + " " + filePath;
            }
            spaceSeparatedFilePaths = spaceSeparatedFilePaths.trim();

            containerNode.setAttribute("name",container.getName() + Integer.toString(containers.size()));
            containerNode.setAttribute("id",container.getId());
            containerNode.setAttribute("location",container.getLocation());
            containerNode.setAttribute("type","SIMPLE");
            containerNode.setAttribute("vmImage",spaceSeparatedFilePaths);

            nodes.add(containerNode);
        }


        for(Link link : links){

            String sourceID = link.getSourceID();
            String targetID = link.getTargetID();

            Element connection = doc.createElement("connection");

            for(int i = 0; i < nodes.size();i++){

                Element intrface = doc.createElement("interface");

                if(nodes.get(i).getAttribute("id").equals(sourceID) || nodes.get(i).getAttribute("id").equals(targetID)){

                    String connectionEnd = "src";
                    if(nodes.get(i).getAttribute("id").equals(targetID))
                        connectionEnd = "dst";

                    String nodeNumber = "0";
                    if(nodes.get(i).hasChildNodes()) {
                        Element lastChild = (Element) nodes.get(i).getLastChild();
                        nodeNumber = Integer.toString(Integer.parseInt(lastChild.getAttribute("id")) + 1);
                    }

                    intrface.setAttribute("id",nodeNumber);
                    intrface.setAttribute("name","GigabitEthernet0/" + nodeNumber);

                    nodes.get(i).appendChild(intrface);
                    connection.setAttribute(connectionEnd,"/virl:topology/virl:node[" + Integer.toString(i+1) + "]/virl:interface[" + Integer.toString(Integer.parseInt(nodeNumber)+1) + "]");
                }
            }
            connections.add(connection);
        }

        //Remove ids
        for(Element node : nodes){
            node.removeAttribute("id");
        }

        //Add all the nodes to the topology
        for(Element node : nodes){
            topology.appendChild(node);
        }

        //Add all the connections to the topology
        for(Element connection : connections){
            topology.appendChild(connection);
        }

        try {
            //Write contents to String
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            transformer.transform(source, result);
            String str = writer.toString();
            System.out.println(str);
        } catch(TransformerException te) {
            te.printStackTrace();
        }
    }

    public static void main(String[] args){
        FormatConverter fc = new FormatConverter();

        String json = fc.readJsonFromFile("src/data.json");

        fc.jsonParser(json);
    }
}