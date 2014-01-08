package com.wix.jettyprobe;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;

public class ProbeTester {
    private final DefaultHttpClient httpClient;
    private final HttpGet httpGet;
    private final HttpPost httpPost;
    private final ResponseHandler<String> handler;
    private static final int connectionTimeOutSec = 5;
    private static final int socketTimeoutSec = 5;

    public ProbeTester() {
        this(new StringResponseHandler());
    }

    public ProbeTester(ResponseHandler handler) {
        httpClient = new DefaultHttpClient();

        final HttpParams httpParameters = httpClient.getParams();

        HttpConnectionParams.setConnectionTimeout(httpParameters, connectionTimeOutSec * 1000);
        HttpConnectionParams.setSoTimeout        (httpParameters, socketTimeoutSec * 1000);

        URI uri = URI.create("http://localhost:9080");
        httpGet = new HttpGet(uri);
        httpPost = new HttpPost(uri);
        this.handler = handler;
    }

    public void addCookie(String name, String value) {
        BasicClientCookie cookie = new BasicClientCookie(name, value);
        cookie.setDomain(".localhost");
        httpClient.getCookieStore().addCookie(cookie);
    }

    public String executeHttpGet()  {
        try {
            return httpClient.execute(httpGet, handler);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String executeHttpPost(String payload) {
        try {
            httpPost.setEntity(new StringEntity(payload));
            return httpClient.execute(httpPost, handler);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class StringResponseHandler implements ResponseHandler<String> {
        public String handleResponse(HttpResponse response) throws IOException {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                return EntityUtils.toString(entity);
            } else {
                return null;
            }
        }
    }
}