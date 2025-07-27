package com.hmall.cart.constants;

/**
 * 购物车服务缓存常量管理类
 * 统一管理所有缓存相关的key前缀和模式
 */
public class CacheConstants {
    // ==================== 购物车缓存相关 ====================
    /**
     * 用户购物车缓存key前缀
     * 格式: cart:user:{用户ID}
     */
    public static final String CART_USER_CACHE_KEY_PREFIX = "cart:user:";
    // ==================== 分布式锁相关 ====================
    /**
     * 用户购物车查询分布式锁key前缀
     * 格式: lock:cart:user:{用户ID}
     */
    public static final String CART_USER_LOCK_KEY_PREFIX = "lock:cart:user:";
    // ==================== 缓存过期时间 ====================
    /**
     * 用户购物车缓存过期时间（小时）
     */
    public static final long CART_CACHE_EXPIRE_HOURS = 2;
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