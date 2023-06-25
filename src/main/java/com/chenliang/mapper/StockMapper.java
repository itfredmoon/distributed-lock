package com.chenliang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chenliang.pojo.Stock;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * @author chen
 * @date 2023-06-11 22:11
 */
public interface StockMapper extends BaseMapper<Stock> {
    @Update("update db_stock set count = count - #{num} where product_code = #{productCode} and count >= 1")
    void deduct (String productCode,Integer num);

    @Select("select * from db_stock where product_code = #{productCode} and id = 1 for update")
    Stock decuctBySelectFroUpdate(String productCode);

    @Select("select * from db_stock where product_code = #{productCode}")
    List<Stock> selectListByProductCode(String number);

    @Update("update db_stock set version=#{version}+1,count=count-1 where version=#{version} and id=#{id}")
    Integer updateByConfirmVersion(Long id, Integer version);
}
