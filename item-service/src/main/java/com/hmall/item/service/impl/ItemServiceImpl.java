package com.hmall.item.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.item.constants.MQConstants;
import com.hmall.item.constants.CacheConstants;
import com.hmall.item.domain.dto.ItemDTO;
import com.hmall.item.domain.dto.ItemMQDTO;
import com.hmall.item.domain.dto.OrderDetailDTO;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.utils.BeanUtils;
import com.hmall.item.domain.po.Item;
import com.hmall.item.enums.ItemOperate;
import com.hmall.item.mapper.ItemMapper;
import com.hmall.item.service.IItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmall.common.domain.PageQuery;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import com.hmall.common.utils.CollUtils;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * <p>
 * 商品表 服务实现类
 * </p>
 *
 * @author 虎哥
 */
@Service
@RequiredArgsConstructor
public class ItemServiceImpl extends ServiceImpl<ItemMapper, Item> implements IItemService {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    
    // 需要@Qualifier的字段，手动注入
    @Autowired
    @Qualifier("customRedisTemplate")
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional
    public void deductStock(List<OrderDetailDTO> items) {
        String sqlStatement = "com.hmall.item.mapper.ItemMapper.updateStock";
        boolean r = false;
        try {
            r = executeBatch(items, (sqlSession, entity) -> sqlSession.update(sqlStatement, entity));
        } catch (Exception e) {
            throw new BizIllegalException("更新库存异常，可能是库存不足", e);
        }
        if (!r) {
            throw new BizIllegalException("库存不足！");
        }
        // 清理缓存
        for (OrderDetailDTO orderDetail : items) {
            Long id = orderDetail.getItemId();
            redisTemplate.delete(CacheConstants.ITEM_CACHE_KEY_PREFIX + id);
        }
        redisTemplate.delete(redisTemplate.keys(CacheConstants.ITEM_BATCH_CACHE_PATTERN));
        redisTemplate.delete(redisTemplate.keys(CacheConstants.ITEM_PAGE_CACHE_PATTERN));
        // MQ同步ES
        for (OrderDetailDTO orderDetail : items) {
            Long id = orderDetail.getItemId();
            Item item = getById(id);
            ItemDTO itemDTO = BeanUtils.copyBean(item, ItemDTO.class);
            rabbitTemplate.convertAndSend(
                MQConstants.ITEM_SYNC_EXCHANGE_NAME,
                MQConstants.ITEM_SYNC_UPDATE_KEY,
                new ItemMQDTO(ItemOperate.UPDATE, itemDTO)
            );
        }
    }

    @Override
    public List<ItemDTO> queryItemByIds(Collection<Long> ids) {
        return BeanUtils.copyList(listByIds(ids), ItemDTO.class);
    }

    // 批量查询商品缓存优化
    @Override
    public List<ItemDTO> queryItemByIdsWithCache(Collection<Long> ids) {
        if (CollUtils.isEmpty(ids)) {
            return CollUtils.emptyList();
        }
        
        String key = CacheConstants.ITEM_BATCH_CACHE_KEY_PREFIX + ids.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
        
        // 先检查hash是否存在
        Long size = redisTemplate.opsForHash().size(key);
        if (size != null && size > 0) {
            // 从hash中获取所有元素
            List<Object> cached = redisTemplate.opsForHash().values(key);
            if (cached != null && !cached.isEmpty()) {
                return cached.stream()
                    .map(item -> objectMapper.convertValue(item, ItemDTO.class))
                    .collect(Collectors.toList());
            }
        }
        
        // 缓存击穿保护：加分布式锁
        String lockKey = CacheConstants.ITEM_BATCH_LOCK_KEY_PREFIX + ids.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
        boolean lock = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, "1", CacheConstants.LOCK_EXPIRE_SECONDS, TimeUnit.SECONDS));
        if (lock) {
            try {
                // 再检查一次hash
                size = redisTemplate.opsForHash().size(key);
                if (size != null && size > 0) {
                    List<Object> cached = redisTemplate.opsForHash().values(key);
                    if (cached != null && !cached.isEmpty()) {
                        return cached.stream()
                            .map(item -> objectMapper.convertValue(item, ItemDTO.class))
                            .collect(Collectors.toList());
                    }
                }
                
                List<ItemDTO> items = queryItemByIds(ids);
                if (CollUtils.isEmpty(items)) {
                    // 空结果也缓存，防止穿透
                    redisTemplate.opsForHash().put(key, "NULL", "NULL");
                    redisTemplate.expire(key, CacheConstants.NULL_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
                    return CollUtils.emptyList();
                } else {
                    // 使用hash结构存储，每个商品以id为key存储
                    Map<String, ItemDTO> itemMap = items.stream()
                        .collect(Collectors.toMap(item -> String.valueOf(item.getId()), item -> item));
                    redisTemplate.opsForHash().putAll(key, itemMap);
                    redisTemplate.expire(key, CacheConstants.ITEM_BATCH_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
                    return items;
                }
            } finally {
                redisTemplate.delete(lockKey);
            }
        } else {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            return queryItemByIdsWithCache(ids);
        }
    }

    @Override
    public void restoreStock(List<OrderDetailDTO> items) {
        for (OrderDetailDTO orderDetail : items) {
            Item item = lambdaQuery().eq(Item::getId, orderDetail.getItemId()).one();
            lambdaUpdate()
                    .set(Item::getStock, item.getStock() + orderDetail.getNum())
                    .eq(Item::getId, orderDetail.getItemId())
                    .update();
        }
        // 清理缓存
        for (OrderDetailDTO orderDetail : items) {
            Long id = orderDetail.getItemId();
            redisTemplate.delete(CacheConstants.ITEM_CACHE_KEY_PREFIX + id);
        }
        redisTemplate.delete(redisTemplate.keys(CacheConstants.ITEM_BATCH_CACHE_PATTERN));
        redisTemplate.delete(redisTemplate.keys(CacheConstants.ITEM_PAGE_CACHE_PATTERN));
        // MQ同步ES
        for (OrderDetailDTO orderDetail : items) {
            Long id = orderDetail.getItemId();
            Item item = getById(id);
            ItemDTO itemDTO = BeanUtils.copyBean(item, ItemDTO.class);
            rabbitTemplate.convertAndSend(
                MQConstants.ITEM_SYNC_EXCHANGE_NAME,
                MQConstants.ITEM_SYNC_UPDATE_KEY,
                new ItemMQDTO(ItemOperate.UPDATE, itemDTO)
            );
        }
    }

    // 分页查询商品缓存优化
    public Page<Item> pageWithCache(PageQuery query) {
        String key = CacheConstants.ITEM_PAGE_CACHE_KEY_PREFIX + query.getPageNo() + ":" + query.getPageSize() + ":" + (StringUtils.hasText(query.getSortBy()) ? query.getSortBy() : "") + ":" + (query.getIsAsc() != null ? query.getIsAsc() : "");
        Object cache = redisTemplate.opsForValue().get(key);
        Page<Item> page = (Page<Item>) redisTemplate.opsForValue().get(key);
        if (page != null) {
            return page;
        }
        page = super.page(query.toMpPage("update_time", false));
        redisTemplate.opsForValue().set(key, page, CacheConstants.ITEM_PAGE_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        return page;
    }

    // 根据id查询商品缓存优化
    public Item getByIdWithCache(Long id) {
        String key = CacheConstants.ITEM_CACHE_KEY_PREFIX + id;
        // 1. 先查缓存
        Object cache = redisTemplate.opsForValue().get(key);
        if (cache != null) {
            if (cache instanceof Item) {
                return (Item) cache;
            } else if ("NULL".equals(cache)) {
                return null;
            }
        }
        // 2. 缓存击穿保护：加分布式锁
        String lockKey = CacheConstants.ITEM_LOCK_KEY_PREFIX + id;
        boolean lock = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, "1", CacheConstants.LOCK_EXPIRE_SECONDS, TimeUnit.SECONDS));
        if (lock) {
            try {
                // 再查一次缓存，防止并发下重复查库
                cache = redisTemplate.opsForValue().get(key);
                if (cache != null) {
                    if (cache instanceof Item) {
                        return (Item) cache;
                    } else if ("NULL".equals(cache)) {
                        return null;
                    } else {
                        return objectMapper.convertValue(cache, Item.class);
                    }
                }
                // 查数据库
                Item item = super.getById(id);
                if (item != null) {
                    redisTemplate.opsForValue().set(key, item, CacheConstants.ITEM_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
                } else {
                    // 缓存空对象，防止穿透
                    redisTemplate.opsForValue().set(key, "NULL", CacheConstants.NULL_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
                }
                return item;
            } finally {
                redisTemplate.delete(lockKey);
            }
        } else {
            // 未获得锁，短暂等待后重试
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            return getByIdWithCache(id);
        }
    }

    // 新增商品时清理分页缓存
    @Override
    public void addItem(ItemDTO itemDTO) {
        Item item = BeanUtils.copyProperties(itemDTO, Item.class);
        baseMapper.insert(item);
        itemDTO.setId(item.getId());
        // 清理所有分页缓存和批量查询缓存
        redisTemplate.delete(redisTemplate.keys(CacheConstants.ITEM_PAGE_CACHE_PATTERN));
        redisTemplate.delete(redisTemplate.keys(CacheConstants.ITEM_BATCH_CACHE_PATTERN));
        rabbitTemplate.convertAndSend(
                MQConstants.ITEM_SYNC_EXCHANGE_NAME,
                MQConstants.ITEM_SYNC_UPDATE_KEY,
                new ItemMQDTO(ItemOperate.ADD, itemDTO)
        );
    }

    // 更新商品时清理单商品和分页缓存
    @Override
    public boolean updateByIdWithCache(Item item) {
        boolean result = super.updateById(item);
        if (result && item.getId() != null) {
            redisTemplate.delete(CacheConstants.ITEM_CACHE_KEY_PREFIX + item.getId());
            redisTemplate.delete(redisTemplate.keys(CacheConstants.ITEM_PAGE_CACHE_PATTERN));
            redisTemplate.delete(redisTemplate.keys(CacheConstants.ITEM_BATCH_CACHE_PATTERN));
        }
        return result;
    }

    // 对外暴露的自定义更新方法
    @Override
    public void updateItem(ItemDTO itemDTO) {
        // 不允许修改商品状态，所以强制设置为null，更新时，就会忽略该字段
        itemDTO.setStatus(null);
        Item item = BeanUtils.copyBean(itemDTO, Item.class);
        updateByIdWithCache(item);
        // MQ同步ES
        rabbitTemplate.convertAndSend(
                MQConstants.ITEM_SYNC_EXCHANGE_NAME,
                MQConstants.ITEM_SYNC_UPDATE_KEY,
                new ItemMQDTO(ItemOperate.UPDATE, itemDTO)
        );
    }

    // 删除商品时清理单商品和分页缓存（私有，仅供deleteItemById调用）
    private boolean removeByIdWithCache(Long id) {
        boolean result = super.removeById(id);
        if (result) {
            redisTemplate.delete(CacheConstants.ITEM_CACHE_KEY_PREFIX + id);
            redisTemplate.delete(redisTemplate.keys(CacheConstants.ITEM_PAGE_CACHE_PATTERN));
            redisTemplate.delete(redisTemplate.keys(CacheConstants.ITEM_BATCH_CACHE_PATTERN));
        }
        return result;
    }

    @Override
    public void deleteItemById(Long id) {
        removeByIdWithCache(id);
        // MQ同步ES
        ItemDTO itemDTO = ItemDTO.builder().id(id).build();
        rabbitTemplate.convertAndSend(
                MQConstants.ITEM_SYNC_EXCHANGE_NAME,
                MQConstants.ITEM_SYNC_UPDATE_KEY,
                new ItemMQDTO(ItemOperate.REMOVE, itemDTO)
        );
    }
}
