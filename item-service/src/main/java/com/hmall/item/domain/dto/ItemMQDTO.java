package com.hmall.item.domain.dto;

import com.hmall.item.enums.ItemOperate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemMQDTO implements Serializable {
    private ItemOperate operate; //操作类型
    private ItemDTO itemDTO;//ItemDto对象
}
