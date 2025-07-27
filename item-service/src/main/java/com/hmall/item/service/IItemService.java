package com.hmall.item.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmall.common.domain.PageQuery;
import com.hmall.item.domain.dto.ItemDTO;
import com.hmall.item.domain.dto.OrderDetailDTO;
import com.hmall.item.domain.po.Item;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 商品表 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-05
 */
public interface IItemService extends IService<Item> {

    void deductStock(List<OrderDetailDTO> items);

    List<ItemDTO> queryItemByIds(Collection<Long> ids);

    void restoreStock(List<OrderDetailDTO> items);

    void addItem(ItemDTO item);

    // 分页缓存查询
    Page<Item> pageWithCache(PageQuery query);
    // 单商品缓存查询
    Item getByIdWithCache(Long id);
    // 批量查询商品缓存查询
    List<ItemDTO> queryItemByIdsWithCache(Collection<Long> ids);
    // 自定义删除商品方法，带缓存清理
    void deleteItemById(Long id);
    // 自定义更新商品方法，带缓存清理
    void updateItem(ItemDTO itemDTO);
    // 更新商品状态，带缓存清理
    boolean updateByIdWithCache(Item item);
}
