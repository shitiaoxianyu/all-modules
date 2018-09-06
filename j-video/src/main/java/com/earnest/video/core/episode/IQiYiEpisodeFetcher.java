package com.earnest.video.core.episode;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.earnest.crawler.core.Browser;
import com.earnest.crawler.core.proxy.HttpProxyPoolSetter;
import com.earnest.video.entity.Episode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.util.Assert;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Slf4j
public class IQiYiEpisodeFetcher extends HttpProxyPoolSetter implements EpisodeFetcher {

    private final CloseableHttpClient httpClient;

    private final ResponseHandler<String> stringResponseHandler;

    private static final String CALLBACK_JS_FUNCTION_PREFIX = "window.Q.__callbacks__.";

    private static final String LETTER_AND_NUMBER = "qwertyuiopasdfghjklzxcvbnm1234567890";

    private static final String API_URL = "http://cache.video.iqiyi.com/jp/avlist/${albumId}/${page}/${size}/?albumId=${albumId}&pageNum=${size}&pageNo=${page}&callback=" + CALLBACK_JS_FUNCTION_PREFIX;

    private static final Pattern episodeExtractPattern = Pattern.compile("\"vlist\":(\\[\\{.+\\}\\])");

    private static final Pageable DEFAULT_EPISODE_PAGE = new PageRequest(1, 50);

    public IQiYiEpisodeFetcher(CloseableHttpClient httpClient, ResponseHandler<String> stringResponseHandler) {
        this.httpClient = httpClient;
        this.stringResponseHandler = stringResponseHandler;
    }


    @Override
    public List<Episode> fetch(String url, Pageable episodePage) throws IOException {

        episodePage = Optional.ofNullable(episodePage).orElse(DEFAULT_EPISODE_PAGE);

        String[] s = url.split("\\?");

        String requestUrl = StringUtils.replaceAll(API_URL, "\\$\\{albumId}", getAlbumId(s))
                .replaceAll("\\$\\{page}", String.valueOf(episodePage.getPageNumber()))
                .replaceAll("\\$\\{size}", String.valueOf(episodePage.getPageSize()))
                + generateRandomJsCallback();

        log.debug("Get the API request address:{},start sending http request", requestUrl);

        HttpUriRequest httpGet = createHttpRequest(s[0], requestUrl);

        String entityString = httpClient.execute(httpGet, stringResponseHandler);


        log.info("connect {} is  successful", httpGet.getRequestLine().getUri());


        return extractJsonString(entityString);
    }

    /**
     * 当Url中携带了<code>albumId</code>时，则进行解析。否则执行{@link #getAlbumIdByHttp(String)}。
     *
     * @param s 请求跳转的<code>Url</code>和携带的<code>albumId</code>（如果有的话）。
     * @return <code>AlbumId</code>。
     * @throws IOException 在执行{@link #getAlbumIdByHttp(String)}时抛出。
     */
    private static String getAlbumId(String[] s) throws IOException {
        String url = s[0]; //请求的地址
        if (s.length < 2) {
            return getAlbumIdByHttp(url);
        }
        return URLEncodedUtils.parse(s[1], Charset.defaultCharset())
                .stream()
                .filter(nameValuePair -> StringUtils.equalsIgnoreCase("albumId", nameValuePair.getName()))
                .map(NameValuePair::getValue)
                .findAny()
                .orElse(getAlbumIdByHttp(url));
    }

    //TODO 这里只是初略的记录特征
    @Override
    public boolean support(String url) {
        Assert.hasText(url, "url is empty or null");
        return URI.create(url).getHost().contains("iqiyi.com");
    }

    /**
     * 将响应的字符串进行提取。
     *
     * @param entityString
     * @return
     */
    private static List<Episode> extractJsonString(String entityString) {
        Matcher matcher = episodeExtractPattern.matcher(entityString);

        List<Episode> episodes = null;
        if (matcher.find()) {
            String episodeJsonString = matcher.group(1);
            if (StringUtils.isNotBlank(episodeJsonString)) {
                episodes = JSONArray.parseArray(episodeJsonString)
                        .stream()
                        .map(e -> (JSONObject) e)
                        .map(mapToEpisodeEntity()).collect(Collectors.toList());
            }
        }
        return episodes;
    }

    private static Function<JSONObject, Episode> mapToEpisodeEntity() {
        return e -> {
            Episode episode = new Episode();
            episode.setDescription(e.getString("desc"));
            episode.setTitle(e.getString("shortTitle"));
            episode.setTimeLength(e.getIntValue("timeLength"));
            episode.setPlayUrl(e.getString("vurl"));
            episode.setVId(e.getString("vid"));
            episode.setShortDescription(e.getString("vt"));
            episode.setId(e.getString("id"));
            episode.setNumber(e.getIntValue("pd"));
            episode.setImage(e.getString("vpic"));
            return episode;
        };
    }

    /**
     * 创建<code>GET</code>请求方式的请求头。
     *
     * @param url        设置请求头<code>Referer</code>的指向地址。
     * @param requestUrl 需要请求的Url。
     * @return {@link org.apache.http.client.methods.HttpGet}
     */
    private HttpUriRequest createHttpRequest(String url, String requestUrl) {

        RequestBuilder requestBuilder = RequestBuilder.get(requestUrl)
                .addHeader(Browser.USER_AGENT, Browser.GOOGLE.userAgent())
                .addHeader(Browser.REFERER, url)
                .setCharset(Charset.defaultCharset());

        addHttpProxySetting(requestBuilder);

        return requestBuilder
                .build();
    }

    /**
     * 获取爱奇艺的<code>AlbumId</code>。
     *
     * @param url 请求的Url。
     * @return <code>AlbumId</code>。
     * @throws IOException 获取失败时抛出。
     */
    private static String getAlbumIdByHttp(String url) throws IOException {

        Connection connection = Jsoup.connect(url)
                .userAgent(Browser.GOOGLE.userAgent())
                .ignoreContentType(true)
                .validateTLSCertificates(false)
                .referrer(url)
                .timeout(5000);

        //获取cookies
//        connection.response().cookies();
        //get headers
//        connection.response().headers();

        Element body = connection
                .get().body();


        return body.select("span.effect-score").attr("data-score-tvid");

    }

    private static String generateRandomJsCallback() {
        return RandomStringUtils.random(8, LETTER_AND_NUMBER);
    }


    @Override
    public void close() throws IOException {
        httpClient.close();
    }

}