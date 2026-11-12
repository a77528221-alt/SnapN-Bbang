package com.snapnbbang.mapper;

import com.snapnbbang.domain.Settlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SettlementMapper {

    int insert(Settlement settlement);

    Settlement selectById(@Param("id") String id);

    int deleteById(@Param("id") String id);
}
