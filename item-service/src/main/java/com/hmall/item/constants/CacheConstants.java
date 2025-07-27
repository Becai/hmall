package com.hmall.item.constants;

/**
 * 商品服务缓存常量管理类
 * 统一管理所有缓存相关的key前缀和模式
 */
public class CacheConstants {
    // ==================== 商品缓存相关 ====================
    /**
     * 单个商品缓存key前缀
     * 格式: item:id:{商品ID}
     */
    public static final String ITEM_CACHE_KEY_PREFIX = "item:id:";
    /**
     * 批量商品缓存key前缀
     * 格式: item:batch:{商品ID列表，逗号分隔}
     */
    public static final String ITEM_BATCH_CACHE_KEY_PREFIX = "item:batch:";
    /**
     * 商品分页缓存key前缀
     * 格式: item:page:{页码}:{页大小}:{排序字段}:{排序方向}
     */
    public static final String ITEM_PAGE_CACHE_KEY_PREFIX = "item:page:";
    // ==================== 分布式锁相关 ====================
    /**
     * 单个商品查询分布式锁key前缀
     * 格式: lock:item:id:{商品ID}
     */
    public static final String ITEM_LOCK_KEY_PREFIX = "lock:item:id:";
    /**
     * 批量商品查询分布式锁key前缀
     * 格式: lock:item:batch:{商品ID列表，逗号分隔}
     */
    public static final String ITEM_BATCH_LOCK_KEY_PREFIX = "lock:item:batch:";
    // ==================== 缓存清理模式 ====================
    /**
     * 商品分页缓存清理模式
     * 用于清理所有分页缓存
     */
    public static final String ITEM_PAGE_CACHE_PATTERN = "item:page:*";
    /**
     * 商品批量缓存清理模式
     * 用于清理所有批量查询缓存
     */
    public static final String ITEM_BATCH_CACHE_PATTERN = "item:batch:*";
    // ==================== 缓存过期时间 ====================
    /**
     * 单个商品缓存过期时间（分钟）
     */
    public static final long ITEM_CACHE_EXPIRE_MINUTES = 30;
    /**
     * 商品分页缓存过期时间（分钟）
     */
    public static final long ITEM_PAGE_CACHE_EXPIRE_MINUTES = 30;
    /**
     * 商品批量缓存过期时间（分钟）
     */
    public static final long ITEM_BATCH_CACHE_EXPIRE_MINUTES = 30;
    /**
     * 空值缓存过期时间（分钟）
     * 用于防止缓存穿透
     */
    public static final long NULL_CACHE_EXPIRE_MINUTES = 5;
    /**
     * 分布式锁过期时间（秒）
     */
    public static final long LOCK_EXPIRE_SECONDS = 10;
} 