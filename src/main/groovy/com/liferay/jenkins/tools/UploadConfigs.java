package com.liferay.jenkins.tools;

import org.apache.commons.io.FileUtils;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;

import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class UploadConfigs {

	public static void main(String[] args) throws Exception {
		CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

		Credentials credentials = new UsernamePasswordCredentials(System.getProperty("username"), System.getProperty("password"));

		credentialsProvider.setCredentials(AuthScope.ANY, credentials);

		RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(1000).setExpectContinueEnabled(true).build();

		HttpClient httpClient = HttpClientBuilder.create()
				.setDefaultCredentialsProvider(credentialsProvider)
				.setDefaultRequestConfig(requestConfig)
				.setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy())
				.disableContentCompression()
				.build();

		RestService restService = new RestService(httpClient, credentialsProvider);

		File jenkinsDirectory = new File("/opt/dev/projects/github/liferay-jenkins-ee");

		String masters;
		String jobs;
		boolean delete;

		if (System.getProperty("masters") != null) {
			masters = System.getProperty("masters");
		}
		else {
			masters = "test-4-1";
		}

		if (System.getProperty("jobs") != null) {
			jobs = System.getProperty("jobs");
		}
		else {
			jobs = "";
		}

		if (System.getProperty("delete") != null) {
			delete = Boolean.parseBoolean(System.getProperty("delete"));
		}
		else {
			delete = false;
		}

		update(restService, jenkinsDirectory, masters, jobs, delete);
	}

	public static void update(RestService restService, File jenkinsDirectory, String mastersString, String jobsString, boolean deleteUnused)
			throws IOException, URISyntaxException, ParserConfigurationException, TransformerException, SAXException, UnableToReadConfigException {

		File mastersDirectory = new File(jenkinsDirectory, "masters");

		List<JobConfig> jobConfigs = new ArrayList<>();

		List<File> masterDirectories = getDirectories(mastersDirectory);

		List<String> masters = Arrays.asList(mastersString.split(","));

		masterDirectories = masterDirectories.stream()
				.filter(file -> masters.contains(file.getName()))
				.collect(Collectors.toList());

		for (File masterDirectory : masterDirectories) {
			String master = masterDirectory.getName();

			URL jenkinsURL = new URL("http://" + master);

			List<JobConfig> masterJobConfigs = getExistingJobConfigs(restService, jenkinsURL);

			File jobsDirectory = new File(masterDirectory, "jobs");

			List<File> jobsDirectories = getDirectories(jobsDirectory);

			if(!jobConfigs.isEmpty()) {
				List<String> jobs = Arrays.asList(jobsString.split(","));

				jobsDirectories = jobsDirectories.stream()
						.filter(file -> containsSubstring(jobs, file.getName()))
						.collect(Collectors.toList());
			}

			for (File jobDirectory : jobsDirectories) {
				String jobName = jobDirectory.getName();

				File configFile = new File(jobDirectory, "config.xml");

				JobConfig jobConfig = getJobConfig(masterJobConfigs, master, jobName);

				if (jobConfig != null) {
					jobConfig.setLocalConfigFile(configFile);
				}
				else {
					jobConfigs.add(new JobConfig(jenkinsURL, jobName, false, configFile));
				}
			}

			jobConfigs.addAll(masterJobConfigs);
		}

		if (deleteUnused) {
			List<JobConfig> unusedJobConfigs = jobConfigs.stream()
					.filter(jobConfig -> !jobConfig.hasLocalConfigFile())
					.collect(Collectors.toList());

			System.out.println("To be delete: ");

			for (JobConfig jobConfig : unusedJobConfigs) {
				System.out.println(jobConfig.getJenkinsURL().toString() + " " + jobConfig.getName());

				jobConfig.delete(restService);
			}
		}

		List<JobConfig> existingJobConfigs = jobConfigs.stream()
				.filter(jobConfig -> jobConfig.hasLocalConfigFile())
				.filter(jobConfig -> jobConfig.exists())
				.collect(Collectors.toList());

		System.out.println("To be updated: ");

		for (JobConfig jobConfig : existingJobConfigs) {
			System.out.println(jobConfig.getJenkinsURL().toString() + " " + jobConfig.getName());

			jobConfig.update(restService);
		}

		List<JobConfig> newJobConfigs = jobConfigs.stream()
				.filter(jobConfig -> jobConfig.hasLocalConfigFile())
				.filter(jobConfig -> !jobConfig.exists())
				.collect(Collectors.toList());

		System.out.println("To be created: ");

		for (JobConfig jobConfig : newJobConfigs) {
			System.out.println(jobConfig.getJenkinsURL().toString() + " " + jobConfig.getName());

			jobConfig.create(restService);
		}

		existingJobConfigs.addAll(newJobConfigs);

		for (File masterDirectory : masterDirectories) {
			String master = masterDirectory.getName();

			URL jenkinsURL = new URL("http://" + master);

			List<String> existingViewNames = ViewConfig.getExistingViewNames(restService, jenkinsURL);

			for (String existingViewName : existingViewNames) {
				if (!existingViewName.equals("Top Level")) {
					restService.post(new URL(jenkinsURL, "/view/" + URLEncoder.encode(existingViewName, "UTF-8").replace("+", "%20") + "/doDelete"), null);
				}
			}
		}

		List<ViewConfig> views = new ArrayList<>();

		List<String> topLevelTemplateNames = getTopLevelTemplateNames(jenkinsDirectory);

		List<Pattern> topLevelJobPatterns = getTopLevelTemplatePatterns(topLevelTemplateNames);

		List<JobConfig> topLevelJobConfigs = existingJobConfigs.stream()
				.filter(jobConfig -> matchesPatterns(topLevelJobPatterns, jobConfig.getName()))
				.collect(Collectors.toList());

		Pattern branchedJobPattern = Pattern.compile("(.+)(\\(.+\\))");

		Map<JobConfig, List<JobConfig>> groupedJobConfigMap = new HashMap<>();

		for (JobConfig topLevelJobConfig : topLevelJobConfigs) {
			Matcher branchedJobMatcher = branchedJobPattern.matcher(topLevelJobConfig.getName());

			if (branchedJobMatcher.find()) {
				String rootName = branchedJobMatcher.group(1);
				String branch = branchedJobMatcher.group(2);

				Pattern downstreamJobPattern = Pattern.compile(Pattern.quote(rootName) + ".*" + Pattern.quote(branch));

				List<JobConfig> downstreamJobConfigs = new ArrayList<>();

				for (JobConfig jobConfig : existingJobConfigs) {
					if (downstreamJobPattern.matcher(jobConfig.getName()).matches()) {
						downstreamJobConfigs.add(jobConfig);
					}
				}

				groupedJobConfigMap.put(topLevelJobConfig, downstreamJobConfigs);
			}
		}

		List<ViewConfig> topLevelViewConfigs = new ArrayList<>();

		for (File masterDirectory : masterDirectories) {
			String master = masterDirectory.getName();

			URL jenkinsURL = new URL("http://" + master);

			topLevelViewConfigs.add(new ViewConfig(jenkinsURL, "Top%20Level", new File("build/resources/main/view.xml"), topLevelJobConfigs));
		}

		for (ViewConfig viewConfig : topLevelViewConfigs) {
			viewConfig.update(restService);
		}

		for (JobConfig topLevelJobConfig : groupedJobConfigMap.keySet()) {
			views.add(new ViewConfig(topLevelJobConfig.getJenkinsURL(), topLevelJobConfig.getName(), new File("build/resources/main/view.xml"), groupedJobConfigMap.get(topLevelJobConfig)));
		}

		for (ViewConfig viewConfig : views) {
			viewConfig.upload(restService);
		}
	}

	public static JobConfig getJobConfig(Collection<JobConfig> jobConfigs, String master, String name) {
		for (JobConfig jobConfig : jobConfigs) {
			if (jobConfig.getJenkinsURL().getHost().equals(master) && jobConfig.getName().equals(name)) {
				return jobConfig;
			}
		}

		return null;
	}

	public static List<JobConfig> getExistingJobConfigs(RestService restService, URL jenkinsURL) throws IOException, URISyntaxException {
		JSONObject jsonObject = new JSONObject(restService.get(new URL(jenkinsURL, "/api/json?tree=jobs[name]")));

		JSONArray jobsJsonArray = jsonObject.getJSONArray("jobs");

		List<JobConfig> jobConfigs = new ArrayList<>();

		for (int i = 0; i < jobsJsonArray.length(); i++) {
			JSONObject jobJsonObject = jobsJsonArray.getJSONObject(i);

			String jobName = jobJsonObject.getString("name");

			jobConfigs.add(new JobConfig(jenkinsURL, jobName, true));
		}

		return jobConfigs;
	}

	public static List<File> getDirectories(File parent) {
		List<File> directories = new ArrayList<>();

		for (File file : parent.listFiles()) {
			if (file.isDirectory()) {
				directories.add(file);
			}
		}

		return directories;
	}

	public static List<Pattern> getTopLevelTemplatePatterns(List<String> topLevelTemplateNames) throws IOException {
		List<Pattern> topLevelTemplatePatterns = new ArrayList<>();

		for (String topLevelTemplateName : topLevelTemplateNames) {
			topLevelTemplatePatterns.add(Pattern.compile(topLevelTemplateName + "(\\(.+\\))?"));
		}

		return topLevelTemplatePatterns;
	}

	public static List<String> getTopLevelTemplateNames(File jenkinsDirectory) throws IOException {
		File templateDirectory = new File(jenkinsDirectory, "template");
		File jobsDirectory = new File(templateDirectory, "jobs");

		List<String> topLevelJobNames = new ArrayList<>();

		for (File jobDirectory : getDirectories(jobsDirectory)) {
			File configFile = new File(jobDirectory, "config.xml");

			if (FileUtils.readFileToString(configFile).contains("<!-- Top Level -->")) {
				topLevelJobNames.add(jobDirectory.getName());
			}
		}

		return topLevelJobNames;
	}

	public static boolean matchesPatterns(List<Pattern> patterns, String string) {
		for (Pattern pattern : patterns) {
			if (pattern.matcher(string).matches()) {
				return true;
			}
		}

		return false;
	}

	public static boolean containsSubstring(List<String> list, String substring) {
		for (String string : list) {
			if (string.contains(substring)) {
				return true;
			}
		}

		return false;
	}

}
