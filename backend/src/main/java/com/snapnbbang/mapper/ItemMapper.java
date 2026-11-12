package com.snapnbbang.mapper;

import com.snapnbbang.domain.Item;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ItemMapper {

    int insert(Item item);

    List<Item> selectBySettlementId(@Param("settlementId") String settlementId);
}
