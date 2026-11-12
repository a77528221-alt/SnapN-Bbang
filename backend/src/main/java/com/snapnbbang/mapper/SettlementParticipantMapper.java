package com.snapnbbang.mapper;

import com.snapnbbang.domain.SettlementParticipant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SettlementParticipantMapper {

    int insert(SettlementParticipant participant);

    List<SettlementParticipant> selectBySettlementId(@Param("settlementId") String settlementId);

    SettlementParticipant selectByIdAndSettlementId(
            @Param("id") Long id,
            @Param("settlementId") String settlementId);

    int claimParticipant(
            @Param("id") Long id,
            @Param("settlementId") String settlementId,
            @Param("tokenHash") String tokenHash);

    int releaseClaim(
            @Param("id") Long id,
            @Param("settlementId") String settlementId);

    int reportPayment(
            @Param("id") Long id,
            @Param("settlementId") String settlementId,
            @Param("tokenHash") String tokenHash);

    int cancelReportPayment(
            @Param("id") Long id,
            @Param("settlementId") String settlementId,
            @Param("tokenHash") String tokenHash);

    int confirmPayment(
            @Param("id") Long id,
            @Param("settlementId") String settlementId);
}
