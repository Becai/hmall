package com.hmall.search.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class CategoryAndBrandVo {
    private List<String> category;
    private List<String> brand;
}
