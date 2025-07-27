# 缓存Key管理优化

## 概述

本次优化对 `item-service` 和 `cart-service` 的缓存key进行了统一管理，使用常量类来管理所有缓存相关的key前缀和模式，提高了代码的可维护性和一致性。

## 优化内容

### 1. 创建缓存常量管理类

#### item-service: `CacheConstants.java`
- **位置**: `item-service/src/main/java/com/hmall/item/constants/CacheConstants.java`
- **功能**: 统一管理商品服务所有缓存相关的key前缀、模式和过期时间

**主要常量**:
- `ITEM_CACHE_KEY_PREFIX`: 单个商品缓存key前缀 (`item:id:`)
- `ITEM_BATCH_CACHE_KEY_PREFIX`: 批量商品缓存key前缀 (`item:batch:`)
- `ITEM_PAGE_CACHE_KEY_PREFIX`: 商品分页缓存key前缀 (`item:page:`)
- `ITEM_LOCK_KEY_PREFIX`: 单个商品查询分布式锁key前缀 (`lock:item:id:`)
- `ITEM_BATCH_LOCK_KEY_PREFIX`: 批量商品查询分布式锁key前缀 (`lock:item:batch:`)
- `ITEM_PAGE_CACHE_PATTERN`: 商品分页缓存清理模式 (`item:page:*`)
- `ITEM_BATCH_CACHE_PATTERN`: 商品批量缓存清理模式 (`item:batch:*`)

**过期时间常量**:
- `ITEM_CACHE_EXPIRE_MINUTES`: 单个商品缓存过期时间 (30分钟)
- `ITEM_PAGE_CACHE_EXPIRE_MINUTES`: 商品分页缓存过期时间 (30分钟)
- `ITEM_BATCH_CACHE_EXPIRE_MINUTES`: 商品批量缓存过期时间 (30分钟)
- `NULL_CACHE_EXPIRE_MINUTES`: 空值缓存过期时间 (5分钟)
- `LOCK_EXPIRE_SECONDS`: 分布式锁过期时间 (10秒)

#### cart-service: `CacheConstants.java`
- **位置**: `cart-service/src/main/java/com/hmall/cart/constants/CacheConstants.java`
- **功能**: 统一管理购物车服务所有缓存相关的key前缀和过期时间

**主要常量**:
- `CART_USER_CACHE_KEY_PREFIX`: 用户购物车缓存key前缀 (`cart:user:`)
- `CART_USER_LOCK_KEY_PREFIX`: 用户购物车查询分布式锁key前缀 (`lock:cart:user:`)

**过期时间常量**:
- `CART_CACHE_EXPIRE_HOURS`: 用户购物车缓存过期时间 (2小时)
- `NULL_CACHE_EXPIRE_MINUTES`: 空值缓存过期时间 (5分钟)
- `LOCK_EXPIRE_SECONDS`: 分布式锁过期时间 (10秒)

### 2. 代码重构

#### item-service 重构
- **文件**: `item-service/src/main/java/com/hmall/item/service/impl/ItemServiceImpl.java`
- **优化内容**:
  - 引入 `CacheConstants` 常量类
  - 替换所有硬编码的缓存key为常量引用
  - 统一使用常量定义的过期时间

#### cart-service 重构
- **文件**: `cart-service/src/main/java/com/hmall/cart/service/impl/CartServiceImpl.java`
- **优化内容**:
  - 引入 `CacheConstants` 常量类
  - 替换所有硬编码的缓存key为常量引用
  - 统一使用常量定义的过期时间
