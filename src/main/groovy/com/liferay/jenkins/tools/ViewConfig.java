package com.liferay.jenkins.tools;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ViewConfig extends Config {

    public ViewConfig(URL jenkinsURL, String name, File template, List<JobConfig> jobConfigs) throws ParserConfigurationException, TransformerException, SAXException, IOException {
        this.jenkinsURL = jenkinsURL;
        this.name = name;
        this.template = template;
        this.jobConfigs = jobConfigs;

        xml = getXML();
    }

    public static List<String> getExistingViewNames(RestService restService, URL jenkinsURL) throws IOException, URISyntaxException {
        JSONObject jsonObject = new JSONObject(restService.get(new URL(jenkinsURL, "/api/json?tree=views[name]")));

        JSONArray viewsJsonArray = jsonObject.getJSONArray("views");

        List<String> viewNames = new ArrayList<>();

        for (int i = 0; i < viewsJsonArray.length(); i++) {
            JSONObject viewJsonObject = viewsJsonArray.getJSONObject(i);

            String viewName = viewJsonObject.getString("name");

            viewNames.add(viewName);
        }

        return viewNames;
    }

    public void upload(RestService restService) throws IOException, URISyntaxException {
        List<String> existingViewNames = getExistingViewNames(restService, jenkinsURL);

        if (existingViewNames.contains(name)) {
            update(restService);
        }
        else {
            create(restService);
        }
    }

    public void create(RestService restService) throws IOException, URISyntaxException {
        restService.postString(new URL(jenkinsURL, "/createView?name=" + name), xml);
    }

    public void update(RestService restService) throws IOException, URISyntaxException {
        restService.postString(new URL(jenkinsURL + "/view/" + name + "/config.xml"), xml);
    }

    public void delete(RestService restService) throws IOException, URISyntaxException {
        restService.post(new URL(jenkinsURL, "/view/" + name + "/doDelete"), null);
    }

    @Override
    public String getXML() throws ParserConfigurationException, IOException, SAXException, TransformerException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        factory.setValidating(false);
        factory.setIgnoringElementContentWhitespace(true);

        DocumentBuilder builder = factory.newDocumentBuilder();

        Document document = builder.parse(template);

        Element rootElement = document.getDocumentElement();

        Element nameElement = document.createElement("name");

        nameElement.setTextContent(name);

        rootElement.appendChild(nameElement);

        NodeList nodeList = rootElement.getElementsByTagName("jobNames");

        Element jobNamesElement = (Element) nodeList.item(0);

        for (JobConfig jobConfig : jobConfigs) {
            Element jobElement = document.createElement("string");

            jobElement.setTextContent(jobConfig.getName());

            jobNamesElement.appendChild(jobElement);
        }

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(document);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        StreamResult result = new StreamResult(byteArrayOutputStream);

        transformer.transform(source, result);

        return byteArrayOutputStream.toString();
    }

    protected String xml;
    protected List<JobConfig> jobConfigs;
    protected File template;

}
