package com.snapnbbang.mapper;

import com.snapnbbang.domain.RoundAbsence;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoundAbsenceMapper {

    int insert(RoundAbsence roundAbsence);

    List<RoundAbsence> selectBySettlementId(@Param("settlementId") String settlementId);
}
