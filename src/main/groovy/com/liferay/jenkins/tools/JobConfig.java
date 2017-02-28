package com.liferay.jenkins.tools;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class JobConfig extends Config {

    public JobConfig(URL jenkinsURL, String name, boolean exists) {
        this.jenkinsURL = jenkinsURL;
        this.name = name;
        this.exists = exists;

        localConfigFile = null;
    }

    public JobConfig(URL jenkinsURL, String name, boolean exists, File localConfigFile) throws FileNotFoundException {
        this.jenkinsURL = jenkinsURL;
        this.name = name;
        this.exists = exists;

        setLocalConfigFile(localConfigFile);
    }

    public static List<String> getExistingJobNames(RestService restService, URL jenkinsURL) throws IOException, URISyntaxException {
        JSONObject jsonObject = new JSONObject(restService.get(new URL(jenkinsURL, "/api/json?tree=jobs[name]")));

        JSONArray jobsJsonArray = jsonObject.getJSONArray("jobs");

        List<String> jobNames = new ArrayList<>();

        for (int i = 0; i < jobsJsonArray.length(); i++) {
            JSONObject jobJsonObject = jobsJsonArray.getJSONObject(i);

            String jobName = jobJsonObject.getString("name");

            jobNames.add(jobName);
        }

        return jobNames;
    }

    public void upload(RestService restService) throws UnableToReadConfigException, IOException, URISyntaxException {
        List<String> existingJobNames = getExistingJobNames(restService, jenkinsURL);

        if (existingJobNames.contains(name)) {
            update(restService);
        }
        else {
            create(restService);
        }
    }

    public void create(RestService restService) throws UnableToReadConfigException, IOException, URISyntaxException {
        restService.postString(new URL(jenkinsURL, "/createItem?name=" + name), getXML());
    }

    public void update(RestService restService) throws UnableToReadConfigException, IOException, URISyntaxException {
        restService.postString(new URL(jenkinsURL, "/job/" + name + "/config.xml"), getXML());
    }

    public void delete(RestService restService) throws IOException, URISyntaxException {
        restService.post(new URL(jenkinsURL, "/job/" + name+ "/doDelete"), null);
    }

    public void setLocalConfigFile(File localConfigFile) throws FileNotFoundException {
        if ((localConfigFile == null) || !localConfigFile.isFile()) {
            throw new FileNotFoundException();
        }

        this.localConfigFile = localConfigFile;
    }

    public boolean hasLocalConfigFile() {
        if ((localConfigFile != null) && localConfigFile.isFile()) {
            return true;
        }

        return false;
    }

    public boolean exists() {
        return exists;
    }

    protected File localConfigFile;
    protected boolean exists;

    @Override
    public String getXML() throws UnableToReadConfigException {
        try {
            return FileUtils.readFileToString(localConfigFile, Charset.defaultCharset());
        }
        catch (IOException e) {
            e.printStackTrace();

            throw new UnableToReadConfigException(e);
        }
    }
}
