package com.hmall.item.listener;

import cn.hutool.json.JSONUtil;
import com.hmall.common.utils.BeanUtils;
import com.hmall.item.constants.ElasticConstants;
import com.hmall.item.constants.MQConstants;
import com.hmall.item.domain.dto.ItemDTO;
import com.hmall.item.domain.dto.ItemMQDTO;
import com.hmall.item.domain.po.ItemDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class ItemListener {
    private final RestHighLevelClient client;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MQConstants.DELAY_ORDER_QUEUE_NAME),
            exchange = @Exchange(name = MQConstants.ITEM_EXCHANGE_NAME, delayed = "false"),
            key = MQConstants.ITEM_QUERY_KEY
    ))
    public void listenerItemMessage(ItemMQDTO itemMQDTO) {
        if (itemMQDTO.getItemDTO().getId() == null) return;
        switch (itemMQDTO.getOperate()) {
            case ADD: //添加
                addItemByIndex(itemMQDTO.getItemDTO());
                break;
            case REMOVE://删除
                removeItemByIndex(itemMQDTO.getItemDTO());
                break;
            case UPDATE://更新
                updateItemByIndex(itemMQDTO.getItemDTO());
                break;
            default:
                log.error("未知的操作类型");
        }
    }

    private void removeItemByIndex(ItemDTO item) {
        //直接根据文档Id删除索引库中的商品
        log.info("移除索引库中的商品" + item.getId());
        DeleteRequest request = new DeleteRequest(ElasticConstants.ITEM_INDEX_NAME)
                .id(item.getId().toString());
        try {
            client.delete(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            log.info("移除索引库中的商品出错了:{}", e.getMessage());
        }
    }

    private void updateItemByIndex(ItemDTO item) {
        UpdateRequest request = new UpdateRequest(ElasticConstants.ITEM_INDEX_NAME, item.getId().toString());
        ItemDoc itemDoc = BeanUtils.copyProperties(item, ItemDoc.class);
        itemDoc.setUpdateTime(LocalDateTime.now());//设置更新时间
        request.doc(JSONUtil.toJsonStr(itemDoc), XContentType.JSON);
        try {
            client.update(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            log.info("修改索引库中商品出错了:{}", e.getMessage());
        }
    }

    /**
     * 根据商品ID将商品信息添加到索引库中。
     *
     * @param item 商品信息的DTO对象
     */
    private void addItemByIndex(ItemDTO item) {
        log.info("添加商品到索引库");
        // 将商品DTO对象转换为索引文档对象
        ItemDoc itemDoc = BeanUtils.copyProperties(item, ItemDoc.class);
        // 创建索引请求对象，指定索引名称和文档ID
        IndexRequest request = new IndexRequest(ElasticConstants.ITEM_INDEX_NAME).id(item.getId().toString());
        // 设置索引请求的源数据，将商品文档对象转换为JSON字符串
        request.source(JSONUtil.toJsonStr(itemDoc), XContentType.JSON);
        // 尝试将索引请求发送到Elasticsearch客户端
        try {
            client.index(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            // 如果发送请求时发生IO异常，记录日志并输出错误信息
            log.info("添加商品到索引库出错了:{}" + e.getMessage());
        }
    }
}
