package com.hmall.item.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    @Bean(destroyMethod = "close")
    public RestHighLevelClient restHighLevelClient(@Value("${hm.elasticsearch.host}") String elasticsearchHost) {
        return new RestHighLevelClient(
                RestClient.builder(HttpHost.create(elasticsearchHost))
                        .setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                                .setConnectTimeout(3000)
                                .setSocketTimeout(30000)
                                .setConnectionRequestTimeout(3000))
                        .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                                .setMaxConnTotal(200)
                                .setMaxConnPerRoute(100))
        );
    }
}
