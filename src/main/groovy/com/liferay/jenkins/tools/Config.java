package com.liferay.jenkins.tools;

import java.net.URL;

public abstract class Config {

    public abstract String getXML() throws UnableToReadConfigException;

    public String getName() {
        return name;
    }

    public URL getJenkinsURL() {
        return jenkinsURL;
    }

    protected String name;
    protected URL jenkinsURL;

}
