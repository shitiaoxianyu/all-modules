package com.earnest.video.search;

import com.alibaba.fastjson.JSONObject;
import com.earnest.crawler.core.Browser;
import com.earnest.crawler.core.proxy.HttpProxyPool;
import com.earnest.video.entity.IQiYi;
import com.earnest.video.exception.UnknownException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.HttpClientUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.Assert;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@AllArgsConstructor
@Slf4j
public class IQiYiPlatformHttpClientSearcher implements PlatformSearcher<IQiYi>, Closeable {

    private final HttpClient httpClient;

    private final ResponseHandler<String> responseHandler;
    @Setter
    @Getter
    private HttpProxyPool httpProxyPool;

    private final HttpUriRequest templateHttpUriRequest =
            RequestBuilder.get("http://search.video.iqiyi.com/o")
                    .setHeader(Browser.USER_AGENT, Browser.IPHONE_X.userAgent())
                    .addParameter("if", "html5")
                    .addParameter("timeLength", "0")
                    .addParameter("video_allow_3rd", "1")
                    .addParameter("intent_result_number", "10")
                    .addParameter("intent_category_type", "1")
                    .setCharset(Charset.defaultCharset())
                    .setHeader(Browser.REFERER, "http://m.iqiyi.com/")
                    .build();

    public IQiYiPlatformHttpClientSearcher(HttpClient httpClient, ResponseHandler<String> responseHandler) {
        this(httpClient, responseHandler, null);
    }

    @Override
    public Page<IQiYi> search(String keyword, Pageable pageRequest) throws IOException {
        Assert.hasText(keyword, "keyword is empty or null");


        RequestBuilder httpUriRequestBuilder = RequestBuilder.copy(templateHttpUriRequest)
                .addParameter("key", keyword)
                .addParameter("pageNum", String.valueOf(pageRequest.getPageNumber() + 1))
                .addParameter("size", String.valueOf(pageRequest.getPageSize()));

        setHttpProxyIfPresent(httpUriRequestBuilder);

        HttpUriRequest httpUriRequest = httpUriRequestBuilder.build();

        String uri = httpUriRequest.getRequestLine().getUri();

        log.debug("Start performing search request,keyword:{},url:{}", keyword, uri);

        String result = httpClient.execute(httpUriRequest, responseHandler);

        JSONObject resultJsonObject = JSONObject.parseObject(result);

        String code = resultJsonObject.getString("code");
        if (!StringUtils.equals(code, "A00000")) {
            throw new UnknownException(String.format("An unknown error occurred while connecting to iQiYi:%s,code:%s", uri, code));
        }

        JSONObject dataJsonObject = resultJsonObject.getJSONObject("data");

        List<IQiYi> resultCollections = dataJsonObject.getJSONArray("docinfos")
                .stream()
                .map(e -> (JSONObject) e)
                .map(mapToIQiYiEntity()).collect(Collectors.toList());


        return new PageImpl<>(resultCollections, pageRequest, dataJsonObject.getIntValue("result_num"));
    }

    private static Function<JSONObject, IQiYi> mapToIQiYiEntity() {
        return jsonObject -> {
            IQiYi iQiYi = new IQiYi();
            JSONObject albumDocInfo = jsonObject.getJSONObject("albumDocInfo");
            iQiYi.setTitle(albumDocInfo.getString("albumTitle"));
            iQiYi.setAlbumId(albumDocInfo.getString("albumId"));
            iQiYi.setPlayValue(albumDocInfo.getString("albumLink"));
            iQiYi.setImage(albumDocInfo.getString("albumVImage"));
            iQiYi.setCategory(StringUtils.split(albumDocInfo.getString("channel"), ",")[0]);
            //播放信息
            iQiYi.setPlayInfo(parsePlayInfo(albumDocInfo));
            return iQiYi;

        };
    }

    private void setHttpProxyIfPresent(RequestBuilder httpUriRequestBuilder) {
        if (httpProxyPool != null) {
            httpProxyPool.get()
                    .map(s -> RequestConfig.custom().setProxy(s.getHttpHost()).build())
                    .ifPresent(httpUriRequestBuilder::setConfig);
        }
    }


    private static String parsePlayInfo(JSONObject albumDocInfo) {
        int episode = albumDocInfo.getIntValue("itemTotalNumber");
        return episode == 0 ? null : episode + "集全";
    }

    @Override
    public void close() {
        HttpClientUtils.closeQuietly(httpClient);
    }
}
