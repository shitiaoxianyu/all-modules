package com.earnest.crawler.core.crawler;

import com.alibaba.fastjson.JSONObject;
import com.earnest.crawler.core.downloader.DownloadListener;
import com.earnest.crawler.core.downloader.Downloader;
import com.earnest.crawler.core.downloader.HttpClientDownloader;
import com.earnest.crawler.core.handler.HttpResponseHandler;
import com.earnest.crawler.core.handler.RegexHttpResponseHandler;
import com.earnest.crawler.core.pipe.Pipeline;
import com.earnest.crawler.core.request.HttpGetRequest;
import com.earnest.crawler.core.request.HttpRequest;
import com.earnest.crawler.core.scheduler.BlockingQueueScheduler;
import com.earnest.crawler.core.scheduler.Scheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static java.util.Collections.singleton;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

@Slf4j
public class SpiderBuilder {

    private Scheduler scheduler;

    private HttpResponseHandler responseHandler;

    private Downloader downloader;

    private Pipeline<?> pipeline;

    private Set<Consumer<?>> persistenceConsumers;

    private Set<DownloadListener> downloadListeners;

    private ExecutorService threadPool;

    private BasicCrawler crawler;

    private int threadNumber = 1;

    SpiderBuilder() {
    }

    public static  SpiderBuilder create() {
        return new SpiderBuilder();
    }

    public SpiderBuilder thread(int num) {
        threadNumber = num;
        //设置最大连接数，系统默认是5x2
        int maxConnectionCount = (int) Math.ceil(((double) num / 2));
        System.getProperties().setProperty("http.maxConnections", String.valueOf(maxConnectionCount));
        log.info("set SystemProperty value: [http.maxConnections={}]", maxConnectionCount * 2);
        return this;
    }

    public SpiderBuilder from(String url) {
        return from(new HttpGetRequest(url));
    }

    public SpiderBuilder from(HttpRequest httpRequest) {
        scheduler = new BlockingQueueScheduler();
        scheduler.offer(httpRequest);
        //

        return this;
    }

    public SpiderBuilder addRequest(HttpRequest httpRequest) {
        if (Objects.isNull(scheduler)) {
            return from(httpRequest);
        } else {
            scheduler.offer(httpRequest);
        }
        return this;
    }

    public SpiderBuilder downloader(Downloader downloader) {
        this.downloader = downloader;
        return this;
    }

    public SpiderBuilder addDownloaderListener(DownloadListener downloadListener) {
        if (nonNull(downloadListener)) {
            if (Objects.isNull(downloadListeners)) {
                downloadListeners = new HashSet<>(5);
            }
            downloadListeners.add(downloadListener);
        }
        return this;
    }

    public <T> SpiderBuilder start() {
        BasicCrawler worker = this.<T>createWorker();

        Integer i = threadNumber;
        threadPool = Executors.newFixedThreadPool(threadNumber);

        while (i > 0) {
            Thread thread = new Thread(worker);
            threadPool.execute(thread);
            i--;
            log.info("Thread {} is turned on", thread.getName());
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    private <T> BasicCrawler createWorker() {
        Assert.state(nonNull(scheduler), "The URL that started crawling is not set");
        crawler = new BasicCrawler();
        //set Downloader
        decideDownloader();
        //--set Downloader

        crawler.setPipeline(defaultIfNull(pipeline, (Pipeline<T>) httpResponse -> (T) httpResponse));

        crawler.setPersistenceConsumers(defaultIfNull(persistenceConsumers, singleton((Consumer<T>) System.out::println)));

        crawler.setScheduler(scheduler);
        //set HttpResponseHandler
        decideHttpResponseHandler();
        //--set HttpResponseHandler

        return crawler;
    }

    private void decideHttpResponseHandler() {
        if (Objects.isNull(responseHandler)) {
            //只会爬取一页
            crawler.setHttpResponseHandler(httpResponse -> Collections.emptySet());
            log.warn("Since {} is not set, only {} will be downloaded", HttpResponseHandler.class, JSONObject.toJSONString(scheduler));
        } else {
            crawler.setHttpResponseHandler(responseHandler);
        }
    }

    private void decideDownloader() {

        Downloader defaultDownloader = defaultIfNull(this.downloader, new HttpClientDownloader());

        if (scheduler instanceof DownloadListener) {
            ConcurrentHashMap.KeySetView<DownloadListener, Boolean> downloadListenersSet = ConcurrentHashMap.newKeySet();
            downloadListenersSet.add(((DownloadListener) scheduler));
            downloadListeners = Optional.ofNullable(downloadListeners)
                    .orElse(downloadListenersSet);
        }

        if (!CollectionUtils.isEmpty(downloadListeners)) {
            if (defaultDownloader instanceof HttpClientDownloader) {
                downloadListeners.forEach(((HttpClientDownloader) defaultDownloader)::addDownloadListener);
            }
        }


        crawler.setDownloader(defaultDownloader);
    }

    public SpiderBuilder match(String regex) {
        responseHandler = new RegexHttpResponseHandler(regex);
        return this;
    }

    public SpiderBuilder stop() {
        threadPool.shutdown();
        return this;
    }

    public SpiderBuilder pipeline(Pipeline pipeline) {
        this.pipeline = pipeline;
        return this;
    }

    public <T> SpiderBuilder addConsumer(Consumer<T> persistenceConsumer) {
        if (nonNull(persistenceConsumer)) {
            if (Objects.isNull(persistenceConsumers)) {
                persistenceConsumers = new HashSet<>(2);
            }
            persistenceConsumers.add(persistenceConsumer);
        }
        return this;
    }
}
