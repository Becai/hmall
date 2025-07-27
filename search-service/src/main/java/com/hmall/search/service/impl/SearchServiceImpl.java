package com.hmall.search.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.common.domain.PageDTO;
import com.hmall.common.utils.CollUtils;
import com.hmall.search.domain.po.Item;
import com.hmall.search.domain.po.ItemDoc;
import com.hmall.search.domain.query.ItemPageQuery;
import com.hmall.search.domain.vo.CategoryAndBrandVo;
import com.hmall.search.mapper.SearchMapper;
import com.hmall.search.service.ISearchService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SearchServiceImpl extends ServiceImpl<SearchMapper, Item> implements ISearchService {
    
    @Resource
    private RestHighLevelClient restHighLevelClient;
    
    @Override
    public PageDTO<ItemDoc> EsSearch(ItemPageQuery query) {
        PageDTO<ItemDoc> result = new PageDTO<>();
        //1.构造请求
        SearchRequest searchRequest = new SearchRequest("items");
        //2.构造查询条件
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        //精准总数
        searchRequest.source().trackTotalHits(true);
        if (query.getKey()!=null && !"".equals(query.getKey())) {
            boolQueryBuilder.must(QueryBuilders.matchQuery("name", query.getKey()));
        }
        //高亮
        searchRequest.source().highlighter(
                SearchSourceBuilder.highlight()
                        .field("name")
                        .preTags("<em>")
                        .postTags("</em>")
        );
        //分页
        searchRequest.source().from(query.from()).size(query.getPageSize());//获取页数
        //排序
        if (!"".equals(query.getSortBy())){
            searchRequest.source().sort(query.getSortBy(), query.getIsAsc()? SortOrder.ASC : SortOrder.DESC);
        }else {
            searchRequest.source().sort("updateTime",query.getIsAsc()? SortOrder.ASC : SortOrder.DESC);
        }
        //分类
        if (query.getCategory()!=null && !"".equals(query.getCategory())){
            boolQueryBuilder.filter(QueryBuilders.termQuery("category.keyword", query.getCategory()));
        }
        //品牌
        if (query.getBrand()!=null && !"".equals(query.getBrand())){
            boolQueryBuilder.filter(QueryBuilders.termQuery("brand.keyword", query.getBrand()));
        }
        //价格
        if (query.getMinPrice()!=null && query.getMaxPrice()!=null) {
            boolQueryBuilder.filter(QueryBuilders.rangeQuery("price").gte(query.getMinPrice()).lte(query.getMaxPrice()));
        }

        //排名 广告优先
        FunctionScoreQueryBuilder functionScoreQuery = QueryBuilders.functionScoreQuery(boolQueryBuilder,
                new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                        new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                                QueryBuilders.termQuery("isAD", true),
                                ScoreFunctionBuilders.weightFactorFunction(10)
                        )
                }).boostMode(CombineFunction.SUM);
        
        searchRequest.source().query(functionScoreQuery).sort("_score", SortOrder.DESC);

        try {
            //发起请求
            SearchResponse search = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            //解析结果
            result.setTotal(search.getHits().getTotalHits().value);
            result.setPages(search.getHits().getTotalHits().value%query.getPageSize()==0?search.getHits().getTotalHits().value/query.getPageSize():search.getHits().getTotalHits().value/query.getPageSize()+1);
            final SearchHit[] hits = search.getHits().getHits();
            List<ItemDoc> list=new ArrayList<>();
            for (SearchHit hit : hits) {
                ItemDoc itemDoc = JSONUtil.toBean(hit.getSourceAsString(), ItemDoc.class);
                Map<String, HighlightField> hfs = hit.getHighlightFields();
                if (CollUtils.isNotEmpty(hfs)) {
                    // 5.1.有高亮结果，获取name的高亮结果
                    HighlightField hf = hfs.get("name");
                    if (hf != null) {
                        // 5.2.获取第一个高亮结果片段，就是商品名称的高亮值
                        String hfName = hf.getFragments()[0].string();
                        itemDoc.setName(hfName);
                    }
                }
                list.add(itemDoc);
            }
            result.setList(list);
        } catch (IOException e) {
            log.error("查询ES失败,出现异常", e);
        }
        //返回
        return result;
    }

    //获得分类和品牌的聚合值
    @Override
    public CategoryAndBrandVo getFilters(ItemPageQuery query) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        CategoryAndBrandVo categoryAndBrandVo = new CategoryAndBrandVo();
        // 1.创建Request
        SearchRequest request = new SearchRequest("items");
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        if (query.getKey()!=null && !"".equals(query.getKey())) {
            boolQueryBuilder.must(QueryBuilders.matchQuery("name", query.getKey()));
        }
        if (query.getCategory()!=null && !"".equals(query.getCategory())){
            boolQueryBuilder.filter(QueryBuilders.termQuery("category.keyword", query.getCategory()));
        }
        if (query.getBrand()!=null && !"".equals(query.getBrand())){
            boolQueryBuilder.filter(QueryBuilders.termQuery("brand.keyword", query.getBrand()));
        }
        if (query.getMinPrice()!=null && query.getMaxPrice()!=null){
            boolQueryBuilder.filter(QueryBuilders.rangeQuery("price").gte(query.getMinPrice()).lte(query.getMaxPrice()));
        }
        request.source().query(boolQueryBuilder).size(0);

        request.source().aggregation(
                AggregationBuilders.terms("category_agg").field("category.keyword").size(10)
        );

        request.source().aggregation(
                AggregationBuilders.terms("brand_agg").field("brand.keyword").size(10));
        List<String> categoryList = new ArrayList<>();
        List<String> brandList = new ArrayList<>();
        // 4.发送请求
        try {
            SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
            Aggregations aggregations = response.getAggregations();
            Terms categoryTerms = aggregations.get("category_agg");
            // 5.2.获取聚合中的桶
            List<? extends Terms.Bucket> buckets = categoryTerms.getBuckets();
            for (Terms.Bucket bucket : buckets) {
                // 5.4.获取桶内key
                String category = bucket.getKeyAsString();
                categoryList.add(category);
            }
            Terms brandTerms = aggregations.get("brand_agg");
            // 5.2.获取聚合中的桶
            List<? extends Terms.Bucket> buckets1 = brandTerms.getBuckets();
            for (Terms.Bucket bucket : buckets1) {
                // 5.4.获取桶内key
                String brand = bucket.getKeyAsString();
                brandList.add(brand);
            }
        } catch (IOException e) {
            log.error("发送请求异常", e);
        }
        categoryAndBrandVo.setCategory(categoryList);
        categoryAndBrandVo.setBrand(brandList);
        return categoryAndBrandVo;
    }
}
