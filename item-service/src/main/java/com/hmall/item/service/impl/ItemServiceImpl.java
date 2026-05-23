package com.hmall.item.service.impl;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.api.dto.ItemDTO;
import com.hmall.api.dto.OrderDetailDTO;
import com.hmall.common.domain.PageDTO;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.utils.BeanUtils;
import com.hmall.item.domin.po.Item;
import com.hmall.item.domin.query.ItemPageQuery;
import com.hmall.item.mapper.ItemMapper;
import com.hmall.item.service.IItemService;
import com.hmall.item.service.IItemStockService;
import org.apache.http.HttpHost;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 商品表 服务实现类
 * </p>
 *
 * @author zhj
 */
@Service
public class ItemServiceImpl extends ServiceImpl<ItemMapper, Item> implements IItemService {

    @Autowired
    private RestHighLevelClient client;
    
    @Autowired
    private IItemStockService itemStockService;

    @Value("${hm.elasticsearch.host}")
    private String elasticsearchHost;

    @Override
    @Transactional
    public void deductStock(List<OrderDetailDTO> items) {
        // 使用新的库存服务（支持Redis预扣减）
        itemStockService.deductStock(items);
    }

    @Override
    public List<ItemDTO> queryItemByIds(Collection<Long> ids) {
        return BeanUtils.copyList(listByIds(ids), ItemDTO.class);
    }

    // es
    @Override
    public PageDTO<ItemDTO> searchofes(ItemPageQuery query) throws IOException {

        client = new RestHighLevelClient(RestClient.builder(
                HttpHost.create(elasticsearchHost)
        ));

        SearchRequest request = new SearchRequest("items");

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        //构建条件
        String key = query.getKey();
        if (key == null || "".equals(key)) {
            boolQuery.must(QueryBuilders.matchAllQuery());
        } else {
            boolQuery.must(QueryBuilders.matchQuery("name", key));
        }

        if (query.getCategory() != null && !query.getCategory().equals("")) {
            boolQuery.filter(QueryBuilders.termQuery("category", query.getCategory()));
        }

        if (query.getBrand() != null && !query.getBrand().equals("")) {
            boolQuery.filter(QueryBuilders.termQuery("brand", query.getBrand()));
        }

        if (query.getMaxPrice() != null && query.getMinPrice() != null) {
            boolQuery.filter(QueryBuilders.rangeQuery("price").gte(query.getMinPrice()).lte(query.getMaxPrice()));
        }

        request.source().query(boolQuery);
        //设置追踪真实总数，突破10000限制
        request.source().trackTotalHits(true);
        //分页
        int pageSize = query.getPageSize();
        int pageNo = query.getPageNo();
        request.source().from((pageNo - 1) * pageSize).size(pageSize);


        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        //结果解析
        SearchHits hits = response.getHits();
        long value = hits.getTotalHits().value;
        System.out.println("总记录数:" + value);

        List<ItemDTO> list = new ArrayList<>();

        for (SearchHit hit : hits) {
            String source = hit.getSourceAsString();
            list.add(JSONUtil.toBean(source, ItemDTO.class));
        }


        PageDTO<ItemDTO> page = new PageDTO<>();

        //总记录数
        page.setTotal(value);
        //计算总页数
        long pages = 0;
        pages = value % query.getPageSize() == 0 ? value / query.getPageSize() : value / query.getPageSize() + 1;
        page.setPages(pages);
        System.out.println("总页数:" + pages);
        page.setList(list);


        if (client != null) {
            client.close();
        }
        return page;
    }
}
