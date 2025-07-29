package com.example.ordersystem.ordering.service;

import com.example.ordersystem.ordering.dto.ProductDto;
import com.example.ordersystem.ordering.dto.ProductUpdateStockDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

// name 은 eureka 에 등록된 호출할 서비스의 이름
@FeignClient(name = "product-service")
public interface ProductFeign {

    @GetMapping("/product/{productId}")
    ProductDto getProductById(@PathVariable("productId") Long productId, @RequestHeader("X-User-Id") String userId);

    @PutMapping("/product/updatestock")
    void updateProductStock(@RequestBody ProductUpdateStockDto productUpdateStockDto);
}
