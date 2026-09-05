package com.investment.service.product;

import com.investment.exception.InvestmentException;
import com.investment.mapper.ProductMapper;
import com.investment.model.ProductItem;
import com.investment.model.ProductItemRow;
import com.investment.model.ProductRequest;
import com.investment.model.SlotBundle;
import com.investment.enums.SourceMode;
import com.investment.service.slot.SlotOptionService;
import com.investment.util.JsonService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * 理财产品数据服务。
 * 复用旧类名作为兼容外壳，内部已迁移为 investment_product 产品库。
 */
@Service
public class ProductService {

    /** 单次检索从 DB 拉取的最大行数，初排后取 top10 交给 Rank 层。 */
    private static final int SEARCH_LIMIT = 50;
    private static final String SCOPE_WHOLE_BANK = "全行";
    private static final String SCOPE_BRANCH = "分行";
    private static final String SCOPE_MANAGER = "客户经理";
    private static final List<String> ALLOWED_SCOPE_TYPES = List.of(SCOPE_WHOLE_BANK, SCOPE_BRANCH, SCOPE_MANAGER);

    private final ProductMapper productMapper;
    private final SlotOptionService slotOptionService;
    private final JsonService jsonService;
    public ProductService(ProductMapper productMapper, SlotOptionService slotOptionService, JsonService jsonService) {
        this.productMapper = productMapper;
        this.slotOptionService = slotOptionService;
        this.jsonService = jsonService;
    }

    public List<ProductItem> findPersonalProducts(Long userId) {
        return productMapper.findPersonalProducts(userId).stream().map(this::toProductItem).toList();
    }

    public List<ProductItem> findPublicProducts() {
        return productMapper.findPublicProducts().stream().map(this::toProductItem).toList();
    }

    /**
     * PERSONAL 模式空库前置检查。
     * 由 Orchestrator#handleTurn 调用，count > 0 才继续推荐链路。
     */
    public boolean hasPersonalProducts(Long userId) {
        return productMapper.countPersonalProducts(userId) > 0;
    }

    @Transactional
    public ProductItem createPersonalProduct(Long userId, ProductRequest request) {
        validateProductRequest(request);
        ProductItemRow row = toRow(null, SourceMode.PERSONAL, userId, request);
        productMapper.insert(row);
        return toProductItem(row);
    }

    @Transactional
    public ProductItem updatePersonalProduct(Long userId, Long productId, ProductRequest request) {
        validateProductRequest(request);
        ProductItemRow row = toRow(productId, SourceMode.PERSONAL, userId, request);
        int updated = productMapper.updatePersonal(row);
        if (updated == 0) {
            throw new InvestmentException("产品不存在或无权限修改");
        }
        return toProductItem(productMapper.findPersonalById(productId, userId));
    }

    @Transactional
    public void deletePersonalProduct(Long userId, Long productId) {
        int deleted = productMapper.deletePersonal(productId, userId);
        if (deleted == 0) {
            throw new InvestmentException("产品不存在或无权限删除");
        }
    }

    /**
     * 按投资槽位召回可售产品。
     */
    public List<ProductItem> search(SourceMode sourceMode, Long userId, SlotBundle slots) {
        SlotBundle recallSlots = slots == null ? SlotBundle.empty() : slots;
        List<ProductItemRow> rows = productMapper.search(
                sourceMode,
                userId,
                jsonService.toJsonArray(recallSlots.riskPreference()),
                jsonService.toJsonArray(recallSlots.investmentHorizon()),
                jsonService.toJsonArray(recallSlots.liquidityNeed()),
                jsonService.toJsonArray(recallSlots.productType()),
                jsonService.toJsonArray(recallSlots.returnExpectation()),
                jsonService.toJsonArray(recallSlots.customerProfile()),
                jsonService.toJsonArray(recallSlots.restriction()),
                SEARCH_LIMIT
        );
        return rows.stream().map(this::toProductItem).toList();
    }

    private void validateProductRequest(ProductRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new InvestmentException("产品名称不能为空");
        }
        SlotBundle slots = request.toSlots();
        if (slots.productType().isEmpty()) {
            throw new InvestmentException("产品类型至少选择一个标签");
        }
        if (slots.riskPreference().isEmpty()) {
            throw new InvestmentException("产品风险等级至少选择一个标签");
        }
        if (slots.investmentHorizon().isEmpty()) {
            throw new InvestmentException("投资期限至少选择一个标签");
        }
        if (slots.liquidityNeed().isEmpty()) {
            throw new InvestmentException("流动性类型至少选择一个标签");
        }
        slotOptionService.validate(slots);
    }

    private ProductItemRow toRow(Long id, SourceMode sourceMode, Long ownerUserId, ProductRequest request) {
        SlotBundle slots = request.toSlots();
        String scopeType = normalizeScopeType(request.scopeType(), sourceMode);
        Long scopeBranchId = request.scopeBranchId();
        Long scopeManagerId = request.scopeManagerId();
        if (SCOPE_WHOLE_BANK.equals(scopeType)) {
            scopeBranchId = null;
            scopeManagerId = null;
        }
        if (SCOPE_MANAGER.equals(scopeType) && scopeManagerId == null) {
            scopeManagerId = ownerUserId;
        }
        if (SCOPE_BRANCH.equals(scopeType) && scopeBranchId == null) {
            throw new InvestmentException("分行范围的产品需要填写分行ID");
        }
        ProductItemRow row = new ProductItemRow();
        row.setId(id);
        row.setSourceType(sourceMode.name());
        row.setOwnerUserId(ownerUserId);
        row.setScopeType(scopeType);
        row.setScopeBranchId(scopeBranchId);
        row.setScopeManagerId(scopeManagerId);
        row.setProductName(request.name().trim());
        row.setProductCode(request.productCode());
        row.setProductType(first(slots.productType()));
        row.setRiskLevel(first(slots.riskPreference()));
        row.setInvestmentHorizon(first(slots.investmentHorizon()));
        row.setLiquidityType(first(slots.liquidityNeed()));
        row.setMinAmount(request.minAmount());
        row.setReturnType(request.returnType() == null || request.returnType().isBlank()
                ? first(slots.returnExpectation()) : request.returnType().trim());
        row.setTargetCustomer(jsonService.toJsonArray(slots.customerProfile()));
        row.setTags(jsonService.toJsonArray(slots.restriction()));
        row.setStatus(request.status() == null || request.status().isBlank() ? "AVAILABLE" : request.status().trim());
        return row;
    }

    private ProductItem toProductItem(ProductItemRow row) {
        if (row == null) {
            return null;
        }
        SlotBundle slots = new SlotBundle(
                List.of(),
                listOf(row.getInvestmentHorizon()),
                listOf(row.getRiskLevel()),
                listOf(row.getLiquidityType()),
                listOf(row.getReturnType()),
                listOf(row.getProductType()),
                jsonService.fromJsonArray(row.getTargetCustomer()),
                jsonService.fromJsonArray(row.getTags())
        );
        return new ProductItem(
                row.getId(),
                SourceMode.valueOf(row.getSourceType()),
                row.getOwnerUserId(),
                row.getScopeType(),
                row.getScopeBranchId(),
                row.getScopeManagerId(),
                row.getProductName(),
                row.getProductCode(),
                row.getProductType(),
                row.getRiskLevel(),
                row.getInvestmentHorizon(),
                row.getLiquidityType(),
                row.getMinAmount(),
                row.getReturnType(),
                row.getTargetCustomer(),
                row.getStatus(),
                slots,
                0
        );
    }

    private String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private List<String> listOf(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.trim());
    }

    private String normalizeScopeType(String scopeType, SourceMode sourceMode) {
        String normalized = scopeType == null ? "" : scopeType.trim();
        if (normalized.isEmpty()) {
            return sourceMode == SourceMode.PUBLIC ? SCOPE_WHOLE_BANK : SCOPE_MANAGER;
        }
        if (!ALLOWED_SCOPE_TYPES.contains(normalized)) {
            throw new InvestmentException("权限范围只能是全行、分行或客户经理");
        }
        return normalized;
    }
}

