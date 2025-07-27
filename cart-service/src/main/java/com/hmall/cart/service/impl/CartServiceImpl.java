package com.hmall.cart.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.api.client.ItemClient;
import com.hmall.api.dto.ItemDTO;
import com.hmall.cart.config.CartProperties;
import com.hmall.cart.constants.CacheConstants;
import com.hmall.cart.domain.dto.CartFormDTO;
import com.hmall.cart.domain.po.Cart;
import com.hmall.cart.domain.vo.CartVO;
import com.hmall.cart.mapper.CartMapper;
import com.hmall.cart.service.ICartService;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.utils.BeanUtils;
import com.hmall.common.utils.CollUtils;
import com.hmall.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 订单详情表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-05
 */
@Service
@RequiredArgsConstructor
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart> implements ICartService {

    // private final RestTemplate restTemplate;
    //
    // private final DiscoveryClient discoveryClient;

    private final ItemClient itemClient;
    private final CartProperties cartProperties;
    
    // 需要@Qualifier的字段，手动注入
    @Autowired
    @Qualifier("customRedisTemplate")
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void addItem2Cart(CartFormDTO cartFormDTO) {
        // 1.获取登录用户
        Long userId = UserContext.getUser();

        // 2.判断是否已经存在
        if(checkItemExists(cartFormDTO.getItemId(), userId)){
            // 2.1.存在，则更新数量
            baseMapper.updateNum(cartFormDTO.getItemId(), userId);
            clearCartCache(userId);
            return;
        }
        // 2.2.不存在，判断是否超过购物车数量
        checkCartsFull(userId);

        // 3.新增购物车条目
        // 3.1.转换PO
        Cart cart = BeanUtils.copyBean(cartFormDTO, Cart.class);
        // 3.2.保存当前用户
        cart.setUserId(userId);
        // 3.3.保存到数据库
        save(cart);
        clearCartCache(userId);
    }

    @Override
    public List<CartVO> queryMyCartsWithCache() {
        Long userId = UserContext.getUser();
        String key = CacheConstants.CART_USER_CACHE_KEY_PREFIX + userId;
        Object cache = redisTemplate.opsForValue().get(key);
        if (cache != null) {
            if (cache instanceof List) {
                return (List<CartVO>) cache;
            } else if ("NULL".equals(cache)) {
                return CollUtils.emptyList();
            }
        }
        // 缓存击穿保护：加分布式锁
        String lockKey = CacheConstants.CART_USER_LOCK_KEY_PREFIX + userId;
        boolean lock = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, "1", CacheConstants.LOCK_EXPIRE_SECONDS, TimeUnit.SECONDS));
        if (lock) {
            try {
                cache = redisTemplate.opsForValue().get(key);
                if (cache != null) {
                    if (cache instanceof List) {
                        return (List<CartVO>) cache;
                    } else if ("NULL".equals(cache)) {
                        return CollUtils.emptyList();
                    }
                }
                List<CartVO> vos = queryMyCarts();
                if (CollUtils.isEmpty(vos)) {
                    redisTemplate.opsForValue().set(key, "NULL", CacheConstants.NULL_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
                    return CollUtils.emptyList();
                } else {
                    redisTemplate.opsForValue().set(key, vos, CacheConstants.CART_CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                    return vos;
                }
            } finally {
                redisTemplate.delete(lockKey);
            }
        } else {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            return queryMyCartsWithCache();
        }
    }

    /**
     * 清理用户购物车缓存
     * @param userId 用户ID
     */
    private void clearCartCache(Long userId) {
        String key = CacheConstants.CART_USER_CACHE_KEY_PREFIX + userId;
        redisTemplate.delete(key);
    }

    @Override
    public List<CartVO> queryMyCarts() {
        // 1.查询我的购物车列表
        List<Cart> carts = lambdaQuery().eq(Cart::getUserId,  UserContext.getUser()).list();
        if (CollUtils.isEmpty(carts)) {
            return CollUtils.emptyList();
        }

        // 2.转换VO
        List<CartVO> vos = BeanUtils.copyList(carts, CartVO.class);

        // 3.处理VO中的商品信息
        handleCartItems(vos);

        // 4.返回
        return vos;
    }

    private void handleCartItems(List<CartVO> vos) {
        // 1.获取商品id
        Set<Long> itemIds = vos.stream().map(CartVO::getItemId).collect(Collectors.toSet());
        // 2.查询商品
        /*
        // 2.1.根据服务名称获取服务的实例列表
        List<ServiceInstance> instances = discoveryClient.getInstances("item-service");
        if (CollUtil.isEmpty(instances)) {
            return;
        }
        // 2.2.手写负载均衡，从实例列表中挑选一个实例
        ServiceInstance instance = instances.get(RandomUtil.randomInt(instances.size()));
        // 2.3.利用RestTemplate发起http请求，得到http的响应
        ResponseEntity<List<ItemDTO>> response = restTemplate.exchange(
                instance.getUri() + "/items?ids={ids}",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ItemDTO>>() {
                },
                Map.of("ids", CollUtil.join(itemIds, ","))
        );
        // 2.3.解析响应
        if(!response.getStatusCode().is2xxSuccessful()){
            // 查询失败，直接结束
            return;
        }
        List<ItemDTO> items = response.getBody();
        */
        List<ItemDTO> items = itemClient.queryItemByIds(itemIds);
        if (CollUtils.isEmpty(items)) {
            return;
        }
        // 3.转为 id 到 item的map
        Map<Long, ItemDTO> itemMap = items.stream().collect(Collectors.toMap(ItemDTO::getId, Function.identity()));
        // 4.写入vo
        for (CartVO v : vos) {
            ItemDTO item = itemMap.get(v.getItemId());
            if (item == null) {
                continue;
            }
            v.setNewPrice(item.getPrice());
            v.setStatus(item.getStatus());
            v.setStock(item.getStock());
        }
    }

    @Override
    @Transactional
    public void removeByItemIds(Collection<Long> itemIds) {
        // 1.构建删除条件，userId和itemId
        QueryWrapper<Cart> queryWrapper = new QueryWrapper<Cart>();
        queryWrapper.lambda()
                .eq(Cart::getUserId, UserContext.getUser())
                .in(Cart::getItemId, itemIds);
        // 2.删除
        remove(queryWrapper);
        clearCartCache(UserContext.getUser());
    }

    // 重写updateById和removeById，清理缓存
    @Override
    public boolean updateById(Cart cart) {
        boolean result = super.updateById(cart);
        if (result && cart.getUserId() != null) {
            clearCartCache(cart.getUserId());
        }
        return result;
    }

    @Override
    public boolean removeById(Serializable id) {
        Cart cart = getById(id);
        boolean result = super.removeById(id);
        if (result && cart != null && cart.getUserId() != null) {
            clearCartCache(cart.getUserId());
        }
        return result;
    }

    private void checkCartsFull(Long userId) {
        int count = lambdaQuery().eq(Cart::getUserId, userId).count();
        if (count >= cartProperties.getMaxAmount()) {
            throw new BizIllegalException(
                    StrUtil.format("用户购物车课程不能超过{}", cartProperties.getMaxAmount()));
        }
    }

    private boolean checkItemExists(Long itemId, Long userId) {
        int count = lambdaQuery()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getItemId, itemId)
                .count();
        return count > 0;
    }
}
