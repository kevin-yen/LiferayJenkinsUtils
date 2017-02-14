package com.liferay.jenkins.tools

import org.apache.commons.io.IOUtils
import org.apache.http.HttpEntity
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.Credentials
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.AuthCache
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.FileEntity
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.HttpResponse
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.BasicAuthCache
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.HttpClientBuilder

class UploadConfigs {
	private HttpClient httpClient
	private int timeout = 1000
	private CredentialsProvider credentialsProvider

	UploadConfigs() {
		RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(timeout).build()

		httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build()
	}

	UploadConfigs(String username, String password) {
		credentialsProvider = new BasicCredentialsProvider()

		Credentials credentials = new UsernamePasswordCredentials(username, password)

		credentialsProvider.setCredentials(AuthScope.ANY, credentials)

		RequestConfig requestConfig = RequestConfig
				.custom()
				.setConnectTimeout(timeout)
				.setExpectContinueEnabled(true)
				.build()

		httpClient = HttpClientBuilder
				.create()
				.setDefaultCredentialsProvider(credentialsProvider)
				.setDefaultRequestConfig(requestConfig)
				.setKeepAliveStrategy()
				.disableContentCompression()
				.build()
	}

	static void main(String[] args) throws Exception {
		UploadConfigs uploadConfigs = new UploadConfigs(System.getProperty('username'), System.getProperty('password'))
		uploadConfigs.uploadConfig("http://test-4-1", "quick-1", "build/resources/main/test.xml")

		uploadConfigs.close()
	}

	String uploadConfig(String jenkinsURL, String jobName, String file) {
		 HttpResponse httpResponse = updateJob(jenkinsURL, jobName, file)

		 if (httpResponse.getStatusLine().getStatusCode() > 399) {
			createJob(jenkinsURL, jobName, file)
		 }
	}

	String getString(String uri) {
		return getString(new URI(uri))
	}

	String getString(URI uri) {
		HttpGet httpGet = new HttpGet(uri)

		HttpResponse httpResponse = httpClient.execute(httpGet)

		return IOUtils.toString(httpResponse.getEntity().getContent(), Charset.defaultCharset())
	}

	HttpResponse createJob(String jenkinsURL, String jobName, String file) {
		return postFile("${jenkinsURL}/view/Top%20Level/createItem?name=${jobName}", file)
	}

	HttpResponse updateJob(String jenkinsURL, String jobName, String file) {
		return postFile("${jenkinsURL}/job/${jobName}/config.xml", file)
	}

	HttpResponse postFile(String uri, String file) {
		return postFile(new URI(uri), new File(file))
	}

	HttpResponse postFile(URI uri, File file) {
		AuthCache authCache = new BasicAuthCache()

		HttpHost httpHost = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme())

		BasicScheme basicAuth = new BasicScheme()

		authCache.put(httpHost, basicAuth)

		HttpClientContext httpClientContext = HttpClientContext.create()

		httpClientContext.setCredentialsProvider(credentialsProvider)

		httpClientContext.setAuthCache(authCache)

		HttpPost httpPost = new HttpPost(uri)

		HttpEntity fileEntity = new FileEntity(file, ContentType.APPLICATION_XML)

		httpPost.setEntity(fileEntity)

		httpPost.setHeader('Content-Type', 'application/xml')
		httpPost.setHeader('User-Agent', 'curl/7.47.0')
		httpPost.setHeader('Accept', '*/*')

		println(httpPost.getRequestLine())

		HttpResponse httpResponse = httpClient.execute(httpPost, httpClientContext)

		println(httpResponse.getStatusLine())

		return httpResponse
	}

	void close() {
		httpClient.getConnectionManager().shutdown()
	}
}