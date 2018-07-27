package com.earnest.crawler.core.request;

import lombok.Data;
import org.apache.http.HttpHost;
@Data
public class HttpProxy {
    private HttpHost httpHost;
    private String username;
    private String password;
}
