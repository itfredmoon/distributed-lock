package com.chenliang.pojo;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author chen
 * @date 2023-06-11 21:43
 */
@Data
@TableName("db_stock")
public class Stock {
    private Long id;
    private String productCode;
    private String warehouse;
    private Integer count;
    private Integer version;


}
