package com.home.weatherstation.smn;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

public class DataLoader {
    private final HttpClient httpClient;
    private final HttpGet httpGet;

    public DataLoader(String smnDataUrl) {
        httpClient = HttpClientBuilder.create().setRedirectStrategy(new LaxRedirectStrategy()).build();
        httpGet = new HttpGet(smnDataUrl);
    }

    public SmnData loadSmnData() throws IOException {
        HttpEntity entity = httpClient.execute(httpGet).getEntity();
        try (InputStream inputStream = new BufferedInputStream(entity.getContent(), 1024)) {
            return new SmnData(IOUtils.toString(inputStream, "UTF-8"));
        }
    }
}
