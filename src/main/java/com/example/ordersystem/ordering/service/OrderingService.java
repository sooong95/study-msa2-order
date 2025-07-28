package com.example.ordersystem.ordering.service;

import com.example.ordersystem.ordering.domain.Ordering;
import com.example.ordersystem.ordering.dto.OrderCreateDto;
import com.example.ordersystem.ordering.dto.ProductDto;
import com.example.ordersystem.ordering.dto.ProductUpdateStockDto;
import com.example.ordersystem.ordering.repository.OrderingRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;


@Service
@RequiredArgsConstructor
@Transactional
public class OrderingService {
    private final OrderingRepository orderingRepository;
    private final RestTemplate restTemplate;

    public Ordering orderCreate(OrderCreateDto orderDto, String userId){

        String productGetUrl = "http://product-service/procuct/" + orderDto.getProductId();

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("X-User-Id", userId);
        HttpEntity<String> httpEntity = new HttpEntity<>(httpHeaders);

        ResponseEntity<ProductDto> response = restTemplate.exchange(productGetUrl, HttpMethod.GET, httpEntity, ProductDto.class);
        ProductDto productDto = response.getBody();

        int quantity = orderDto.getProductCount();

        if (productDto.getStockQuantity() < quantity) {
            throw new IllegalArgumentException("재고 부족");
        } else {
            String productPutUrl = "http://product-service/product/updatestock";

            httpHeaders.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<ProductUpdateStockDto> updateEntity = new HttpEntity<>(
                    ProductUpdateStockDto.builder()
                            .productId(orderDto.getProductId())
                            .productQuantity(orderDto.getProductCount())
                            .build(), httpHeaders
            );

            restTemplate.exchange(productPutUrl, HttpMethod.PUT, updateEntity, Void.class);
        }

        Ordering ordering = Ordering.builder()
                .memberId(Long.parseLong(userId))
                .productId(orderDto.getProductId())
                .quantity(orderDto.getProductCount())
                .build();
        orderingRepository.save(ordering);
        return  ordering;
    }

}
