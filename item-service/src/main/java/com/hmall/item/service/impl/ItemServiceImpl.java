package com.hmall.item.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.item.constants.MQConstants;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

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
    }

    @Override
    public List<ItemDTO> queryItemByIds(Collection<Long> ids) {
        return BeanUtils.copyList(listByIds(ids), ItemDTO.class);
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
    }

    @Override
    public void addItem(ItemDTO itemDTO) {
        Item item = BeanUtils.copyProperties(itemDTO, Item.class);//复制属性
        baseMapper.insert(item);//插入数据
        itemDTO.setId(item.getId());//设置ID
        rabbitTemplate.convertAndSend(
                MQConstants.ITEM_SYNC_EXCHANGE_NAME,
                MQConstants.ITEM_SYNC_UPDATE_KEY,
                new ItemMQDTO(ItemOperate.ADD, itemDTO)
        );
    }
}
