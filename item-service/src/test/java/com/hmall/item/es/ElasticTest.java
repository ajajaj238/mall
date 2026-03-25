package com.hmall.item.es;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmall.common.utils.CollUtils;
import com.hmall.item.domin.po.Item;
import com.hmall.item.domin.po.ItemDoc;
import com.hmall.item.service.IItemService;
import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.TermVectorsResponse;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.List;

//@SpringBootTest(properties = "spring.profiles.active=local")
public class ElasticTest {

    @Autowired
    private IItemService itemService;
    private RestHighLevelClient client;

    //聚合查询
    @Test
    void testAggs() throws IOException {
        SearchRequest request = new SearchRequest("items");

        request.source().size(0);
        request.source().aggregation(AggregationBuilders.terms("brandAgg").field("brand").size(5));

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        Aggregations aggregations = response.getAggregations();
        Terms brandAgg = aggregations.get("brandAgg");
        List<? extends Terms.Bucket> buckets = brandAgg.getBuckets();
        for (Terms.Bucket bucket : buckets) {
            System.out.println("key:"+bucket.getKeyAsString());
            System.out.println("count::"+bucket.getDocCount());
        }
    }


    @Test
    void querybool() throws IOException {
        SearchRequest request = new SearchRequest("items");

        request.source()
                .query(QueryBuilders.boolQuery()
                        .must(QueryBuilders.matchQuery("name", "脱脂牛奶"))
                        .filter(QueryBuilders.rangeQuery("price").lt(30000))
                        .filter(QueryBuilders.termQuery("brand","德亚"))
                );
        //分页排序
        request.source().from(0).size(5);
        request.source().sort("price", SortOrder.DESC);

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        //解析结果
        SearchHits hits = response.getHits();
        long value = hits.getTotalHits().value;
        System.out.println("一共"+value+"条");
        SearchHit[] searchHits = hits.getHits();

        for (SearchHit searchHit : searchHits) {
            String json = searchHit.getSourceAsString();
            ItemDoc itemDoc = JSONUtil.toBean(json, ItemDoc.class);
            System.out.println("item:"+itemDoc);

        }
    }

    @Test
    void query() throws IOException {

        SearchRequest request = new SearchRequest("items");

        //构建查询条件
        request.source()
                .query(QueryBuilders.matchAllQuery());//查询全部内容

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        //解析结果
        SearchHits hits = response.getHits();
        long value = hits.getTotalHits().value;
        System.out.println("一共"+value+"条");
        SearchHit[] searchHits = hits.getHits();
        for (SearchHit searchHit : searchHits) {
            String json = searchHit.getSourceAsString();
            ItemDoc itemDoc = JSONUtil.toBean(json, ItemDoc.class);
            System.out.println("item:"+itemDoc);

        }
    }

    @Test
    void test() {
        System.out.println("client = " + client);
    }
    private final static String DOC = "";

    //批量导入商品数据
    @Test
    void testLoadItemDocs() throws IOException {
        // 分页查询商品数据
        int pageNo = 1;
        int size = 1000;
        while (true) {
            Page<Item> page = itemService.lambdaQuery()
                    .eq(Item::getStatus, 1)
                    .page(new Page<Item>(pageNo, size));
            // 非空校验
            List<Item> items = page.getRecords();
            if (CollUtils.isEmpty(items)) {
                return;
            }
            // 1.创建Request
            BulkRequest request = new BulkRequest("items");
            // 2.准备参数，添加多个新增的Request
            for (Item item : items) {
                // 2.1.转换为文档类型ItemDTO
                ItemDoc itemDoc = BeanUtil.copyProperties(item, ItemDoc.class);
                // 2.2.创建新增文档的Request对象
                request.add(new IndexRequest()
                        .id(itemDoc.getId())
                        .source(JSONUtil.toJsonStr(itemDoc), XContentType.JSON));
            }
            // 3.发送请求
            client.bulk(request, RequestOptions.DEFAULT);

            // 翻页
            pageNo++;
        }
    }

    //查询文档
    @Test
    void testGetDoc() throws IOException {
        GetRequest request = new GetRequest("items", "1");
        GetResponse response = client.get(request, RequestOptions.DEFAULT);
        String json = response.getSourceAsString();
    }

    //创建文档
    @Test
    void testIndexdoc() throws IOException {
        //可以通过数据库查询
        //准备Request
        IndexRequest request = new IndexRequest("items").id("1");
        //准备请求参数
        request.source(DOC,XContentType.JSON);
        //发送请求
        client.index(request, RequestOptions.DEFAULT);
    }


    //创建索引库
    @Test
    void test1() throws IOException {
        CreateIndexRequest request = new CreateIndexRequest("items");
        request.source(INDEX_NAME, XContentType.JSON);
        client.indices().create(request, RequestOptions.DEFAULT);
    }

    //查询索引库
    @Test
    void test2() throws IOException {
        GetIndexRequest request = new GetIndexRequest("items");
        boolean exists = client.indices().exists(request, RequestOptions.DEFAULT);
        System.out.println("exists:"+exists);
    }
    //删除索引库
    @Test
    void test3() throws IOException {
        DeleteIndexRequest request = new DeleteIndexRequest("items");
        client.indices().delete(request, RequestOptions.DEFAULT);
    }

    private static final String INDEX_NAME = "{\n" +
            "  \"mappings\": {\n" +
            "    \"properties\": {\n" +
            "      \"id\": {\n" +
            "        \"type\": \"keyword\"\n" +
            "      },\n" +
            "      \"name\":{\n" +
            "        \"type\": \"text\",\n" +
            "        \"analyzer\": \"ik_max_word\"\n" +
            "      },\n" +
            "      \"price\":{\n" +
            "        \"type\": \"integer\"\n" +
            "      },\n" +
            "      \"stock\":{\n" +
            "        \"type\": \"integer\"\n" +
            "      },\n" +
            "      \"image\":{\n" +
            "        \"type\": \"keyword\",\n" +
            "        \"index\": false\n" +
            "      },\n" +
            "      \"category\":{\n" +
            "        \"type\": \"keyword\"\n" +
            "      },\n" +
            "      \"brand\":{\n" +
            "        \"type\": \"keyword\"\n" +
            "      },\n" +
            "      \"sold\":{\n" +
            "        \"type\": \"integer\"\n" +
            "      },\n" +
            "      \"commentCount\":{\n" +
            "        \"type\": \"integer\",\n" +
            "        \"index\": false\n" +
            "      },\n" +
            "      \"isAD\":{\n" +
            "        \"type\": \"boolean\"\n" +
            "      },\n" +
            "      \"updateTime\":{\n" +
            "        \"type\": \"date\"\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";

    @BeforeEach
    void setUp() {
        client = new RestHighLevelClient(RestClient.builder(
                HttpHost.create("http://192.168.40.130:9200")
        ));
    }

    @AfterEach
    void tearDown() throws IOException {
        if (client != null){
            client.close();
        }
    }
}
