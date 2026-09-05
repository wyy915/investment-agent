package com.investment.mapper;

import com.investment.model.FeedbackRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FeedbackMapper {
    int insert(
            @Param("userId") Long userId,
            @Param("sessionId") String sessionId,
            @Param("productId") Long productId,
            @Param("action") String action,
            @Param("rating") Integer rating,
            @Param("reason") String reason
    );

    List<FeedbackRow> findBySessions(
            @Param("userId") Long userId,
            @Param("sessionIds") List<String> sessionIds,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );
}

