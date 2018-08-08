package com.earnest.crawler.core.builder;

import com.earnest.crawler.core.request.HttpProxy;
import org.apache.http.client.CookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.springframework.util.Assert;

import java.util.Map;


public class GlobalRequestConfigurer extends RequestConfigConfigurer<CloseableHttpClient> {

    private final HttpClientBuilder httpClientBuilder = HttpClients.custom();

    private final CookieStore cookieStore;

    private int thread;


    public GlobalRequestConfigurer(SpiderBuilder builder, CookieStore cookieStore) {
        super(builder);
        this.cookieStore = cookieStore;
    }

    public GlobalRequestConfigurer setProxy(HttpProxy httpProxy) {
        Assert.notNull(httpProxy, "httpProxy is null");
        httpClientBuilder.setProxy(httpProxy.getHttpHost());
        return this;
    }

    public GlobalRequestConfigurer setThread(int thread) {
        this.thread = thread;
        httpClientBuilder.setMaxConnTotal(this.thread);
        return this;
    }

    public int getThread() {
        return thread;
    }

    public GlobalRequestConfigurer addCookie(String name, String value) {
        cookieStore.addCookie(new BasicClientCookie(name, value));
        return this;
    }

    public GlobalRequestConfigurer userAgent(String userAgent) {
        httpClientBuilder.setUserAgent(userAgent);
        return this;
    }

    public GlobalRequestConfigurer addCookies(Map<String, String> cookies) {
        cookies.forEach(this::addCookie);
        return this;
    }


    @Override
    CloseableHttpClient build() {

        httpClientBuilder
                .setDefaultRequestConfig(requestConfig())
                .setDefaultCookieStore(cookieStore);


        return httpClientBuilder.build();
    }


}
