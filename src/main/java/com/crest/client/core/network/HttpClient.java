package com.crest.client.core.network;

import com.crest.client.core.error.NetworkException;
import com.crest.client.core.logging.CrestLogger;
import com.crest.client.core.logging.LoggerFactory;
import com.crest.client.core.security.UrlValidator;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class HttpClient {
    private static final CrestLogger LOGGER = LoggerFactory.getLogger("HttpClient");
    private static OkHttpClient client;
    
    public static synchronized void initialize() {
        if (client != null) return;
        
        client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build();
        
        LOGGER.info("HTTP client initialized");
    }
    
    public static OkHttpClient getClient() {
        if (client == null) initialize();
        return client;
    }
    
    public static Response get(String url) throws NetworkException {
        if (!UrlValidator.isUrlAllowed(url)) {
            throw new NetworkException("URL not allowed: " + url);
        }
        
        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();
        
        try {
            LOGGER.network("GET {}", url);
            Response response = getClient().newCall(request).execute();
            LOGGER.network("GET {} -> {}", url, response.code());
            return response;
        } catch (IOException e) {
            LOGGER.error("GET {} failed", e, url);
            throw new NetworkException("Failed to fetch: " + url, e);
        }
    }
    
    public static void getAsync(String url, Callback callback) {
        if (!UrlValidator.isUrlAllowed(url)) {
            callback.onFailure(null, new IOException("URL not allowed"));
            return;
        }
        
        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();
        
        LOGGER.network("GET (async) {}", url);
        getClient().newCall(request).enqueue(callback);
    }
}
