package com.liferay.jenkins.tools;

import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.net.URL;

public abstract class Config {

    public abstract String getXML() throws ParserConfigurationException, IOException, SAXException, TransformerException;

    public String getName() {
        return name;
    }

    public URL getJenkinsURL() {
        return jenkinsURL;
    }

    protected String name;
    protected URL jenkinsURL;

}
