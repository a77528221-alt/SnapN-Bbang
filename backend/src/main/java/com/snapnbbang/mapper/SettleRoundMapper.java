package com.snapnbbang.mapper;

import com.snapnbbang.domain.SettleRound;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SettleRoundMapper {

    int insert(SettleRound round);

    List<SettleRound> selectBySettlementId(@Param("settlementId") String settlementId);

    SettleRound selectByIdAndSettlementId(
            @Param("id") Long id,
            @Param("settlementId") String settlementId);
}
