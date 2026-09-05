package com.investment.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 产品重排请求。
 * <p>
 * 重排层消费调整方向和排除列表，避免这些状态只写不读。
 */

@Data
@Accessors(fluent = true)
@AllArgsConstructor
public class ProductRankRequest {

    /**
     * 检索层返回的候选产品。
     */
    private List<ProductItem> candidates;

    /**
     * 本轮合并后的用户槽位。
     */
    private SlotBundle slots;

    /**
     * 需要排除的历史推荐 ID。
     */
    private List<Long> excludeProductIds;
}

