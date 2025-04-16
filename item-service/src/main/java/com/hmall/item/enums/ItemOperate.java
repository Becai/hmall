package com.hmall.item.enums;

import lombok.Getter;

/**
 * 商品操作类型
 */
@Getter
public enum ItemOperate {
    ADD(0, "新增商品"),
    REMOVE(1, "删除商品"),
    UPDATE(2, "修改商品");
    private final int value;
    private final String desc;

    ItemOperate(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
