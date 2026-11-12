package com.snapnbbang.mapper;

import com.snapnbbang.domain.ItemExclusion;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ItemExclusionMapper {

    int insert(ItemExclusion itemExclusion);

    List<ItemExclusion> selectBySettlementId(@Param("settlementId") String settlementId);
}
