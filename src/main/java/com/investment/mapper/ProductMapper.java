package com.investment.mapper;

import com.investment.model.ProductItemRow;
import com.investment.enums.SourceMode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductMapper {
    int insert(ProductItemRow row);

    int updatePersonal(ProductItemRow row);

    int deletePersonal(@Param("id") Long id, @Param("userId") Long userId);

    ProductItemRow findPersonalById(@Param("id") Long id, @Param("userId") Long userId);

    List<ProductItemRow> findPersonalProducts(Long userId);

    List<ProductItemRow> findPublicProducts();

    int countPersonalProducts(Long userId);

    List<ProductItemRow> search(
            @Param("sourceMode") SourceMode sourceMode,
            @Param("userId") Long userId,
            @Param("riskPreferenceJson") String riskPreferenceJson,
            @Param("investmentHorizonJson") String investmentHorizonJson,
            @Param("liquidityNeedJson") String liquidityNeedJson,
            @Param("productTypeJson") String productTypeJson,
            @Param("returnExpectationJson") String returnExpectationJson,
            @Param("customerProfileJson") String customerProfileJson,
            @Param("restrictionJson") String restrictionJson,
            @Param("limit") int limit
    );
}





