package com.hmall.search.listener;

import cn.hutool.json.JSONUtil;
import com.hmall.search.domain.po.ItemDoc;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

@Component
@Slf4j
public class itemUpdateListener {
    @Resource
    private  RestHighLevelClient client;
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "item.change.queue"),
            exchange = @Exchange(name = "es.item.direct"),
            key = "item.change"
    ))
    private void handleItemChange(ItemDoc itemDoc, Message message) throws IOException {
        //建立连接
        client = new RestHighLevelClient(RestClient.builder(
                HttpHost.create("http://192.168.198.128:9200")
        ));
        //判断当前是什么操作
        String method = message.getMessageProperties().getHeader("method");
        if("add".equals(method)){
            // 3.将ItemDTO转json
            String doc = JSONUtil.toJsonStr(itemDoc);
            // 1.准备Request对象
            IndexRequest request = new IndexRequest("items").id(itemDoc.getId());
            // 2.准备Json文档
            request.source(doc, XContentType.JSON);
            // 3.发送请求
            client.index(request, RequestOptions.DEFAULT);
            //新增
        }else if("update".equals(method)){
            //1.准备request对象
            UpdateRequest request = new UpdateRequest("items",itemDoc.getId());
            //2.准备请求体
            request.doc(JSONUtil.toJsonStr(method));
            //3.发送请求
            try {
                client.update(request, RequestOptions.DEFAULT);
            } catch (IOException e) {
                e.printStackTrace();
            }
            //修改
        }else if("delete".equals(method)){
            //删除
            //1.准备request对象
            DeleteRequest request = new DeleteRequest("items").id(itemDoc.getId());
            //2.发送请求
            client.delete(request, RequestOptions.DEFAULT);
        }else {
            log.error("无法匹配操作类型");
        }
        //断开连接
        client.close();
    }
}
