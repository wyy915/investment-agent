package com.investment.controller.product;

import com.investment.constants.InvestmentConstants;
import com.investment.model.ProductRequest;
import com.investment.model.ProductResponse;
import com.investment.service.product.ProductService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/investment/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/personal")
    public List<ProductResponse> findPersonal(@RequestHeader(value = InvestmentConstants.USER_ID, defaultValue = "1") Long userId) {
        return productService.findPersonalProducts(userId).stream().map(ProductResponse::from).toList();
    }

    @PostMapping("/personal")
    public ProductResponse createPersonal(@RequestHeader(value = InvestmentConstants.USER_ID, defaultValue = "1") Long userId, @RequestBody ProductRequest request) {
        return ProductResponse.from(productService.createPersonalProduct(userId, request));
    }

    @PutMapping("/personal/{productId}")
    public ProductResponse updatePersonal(@RequestHeader(value = InvestmentConstants.USER_ID, defaultValue = "1") Long userId, @PathVariable Long productId, @RequestBody ProductRequest request) {
        return ProductResponse.from(productService.updatePersonalProduct(userId, productId, request));
    }

    @DeleteMapping("/personal/{productId}")
    public void deletePersonal(@RequestHeader(value = InvestmentConstants.USER_ID, defaultValue = "1") Long userId, @PathVariable Long productId) {
        productService.deletePersonalProduct(userId, productId);
    }

    @GetMapping("/public")
    public List<ProductResponse> findPublic() {
        return productService.findPublicProducts().stream().map(ProductResponse::from).toList();
    }
}

