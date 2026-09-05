package com.investment.service.product;

import com.investment.exception.InvestmentException;
import com.investment.model.ProductItem;
import com.investment.model.ProductSearchRequest;
import com.investment.enums.SourceMode;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 产品检索服务（Orchestrator 推荐流水线第一层）。
 * 按 sourceMode、userId、slots 从 DB 召回候选，不负责最终重排和 excludeProductIds 过滤。
 */
@Service
public class ProductSearchService {

    /** 底层产品服务，封装 MyBatis JSON_OVERLAPS 检索。 */
    private final ProductService productService;

    /** 构造器注入 ProductService。 */
    public ProductSearchService(ProductService productService) {
        this.productService = productService;
    }

    /**
     * 执行数据源隔离检索。
     * 由 Orchestrator#completeRecommendation 调用；excludeProductIds 在 ProductRankService 层过滤。
     */
    public List<ProductItem> search(ProductSearchRequest request) {
        // 请求体或 sourceMode 为空时抛异常
        if (request == null || request.sourceMode() == null) {
            throw new InvestmentException("sourceMode 不能为空");
        }

        // PERSONAL 模式必须提供 userId，否则无法查个人库
        if (request.sourceMode() == SourceMode.PERSONAL && request.userId() == null) {
            throw new InvestmentException("PERSONAL 模式必须提供 userId");
        }

        // ProductService.search：MySQL JSON_OVERLAPS
        return productService.search(request.sourceMode(), request.userId(), request.slots());
    }
}

