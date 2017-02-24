package com.liferay.jenkins.tools;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;

public class RestService {

    public RestService(HttpClient httpClient, CredentialsProvider credentialsProvider) {
        this.httpClient = httpClient;
        this.credentialsProvider = credentialsProvider;
    }

    public HttpResponse postFile(URL url, File file) throws IOException, URISyntaxException {
        return postFile(url.toURI(), file);
    }

    public HttpResponse postFile(URI uri, File file) throws IOException, URISyntaxException {
        HttpEntity fileEntity = new FileEntity(file, ContentType.APPLICATION_XML);

        return post(uri, fileEntity);
    }

    public HttpResponse postString(URL url, String string) throws IOException, URISyntaxException {
        return postString(url.toURI(), string);
    }

    public HttpResponse postString(URI uri, String string) throws IOException {
        StringEntity stringEntity = new StringEntity(string, ContentType.APPLICATION_XML);

        return post(uri, stringEntity);
    }

    public HttpResponse post(URL url, HttpEntity httpEntity) throws IOException, URISyntaxException {
        return post(url.toURI(), httpEntity);
    }

    public HttpResponse post(URI uri, HttpEntity httpEntity) throws IOException {
        HttpHost httpHost = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());

        authCache.put(httpHost, basicAuth);

        httpClientContext.setCredentialsProvider(credentialsProvider);
        httpClientContext.setAuthCache(authCache);

        HttpPost httpPost = new HttpPost(uri);

        if (httpEntity != null) {
            httpPost.setEntity(httpEntity);
        }

        httpPost.setHeader("User-Agent", "curl/7.47.0");
        httpPost.setHeader("Accept", "*/*");

        System.out.println(httpPost.getRequestLine());

        CloseableHttpResponse httpResponse = (CloseableHttpResponse) httpClient.execute(httpPost, httpClientContext);

        System.out.println(httpResponse.getStatusLine());

        if (httpResponse.getEntity() != null) {
            EntityUtils.consume(httpResponse.getEntity());
        }

        return httpResponse;
    }

    public String get(URL url) throws IOException, URISyntaxException {
        return get(url.toURI());
    }

    public String get(URI uri) throws IOException {
        HttpHost httpHost = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());

        authCache.put(httpHost, basicAuth);

        httpClientContext.setCredentialsProvider(credentialsProvider);
        httpClientContext.setAuthCache(authCache);

        HttpGet httpGet = new HttpGet(uri);

        System.out.println(httpGet.getRequestLine());

        CloseableHttpResponse httpResponse = (CloseableHttpResponse) httpClient.execute(httpGet);

        System.out.println(httpResponse.getStatusLine());

        String content = IOUtils.toString(httpResponse.getEntity().getContent(), Charset.defaultCharset());

        if (httpResponse.getEntity() != null) {
            EntityUtils.consume(httpResponse.getEntity());
        }

        return content;
    }

    private HttpClient httpClient;
    private CredentialsProvider credentialsProvider;

    private AuthCache authCache = new BasicAuthCache();
    private BasicScheme basicAuth = new BasicScheme();
    private HttpClientContext httpClientContext = HttpClientContext.create();

}
