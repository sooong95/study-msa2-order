package com.example.ordersystem.ordering.service;

import com.example.ordersystem.ordering.domain.Ordering;
import com.example.ordersystem.ordering.dto.OrderCreateDto;
import com.example.ordersystem.ordering.dto.ProductDto;
import com.example.ordersystem.ordering.dto.ProductUpdateStockDto;
import com.example.ordersystem.ordering.repository.OrderingRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;


@Service
@RequiredArgsConstructor
@Transactional
public class OrderingService {

    private final OrderingRepository orderingRepository;
    private final RestTemplate restTemplate;
    private final ProductFeign productFeign;
    private final KafkaTemplate<String, Object> kafkaTemplate;

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

    // circuitbreaker 는 각기 다른 name 을 가진 해당 메서드에 한해서만 유효.
    // 즉, circuit 이 open 되어도 다른 메서드에서 product-service 에 요청을 보내는 것은 허용.
    @CircuitBreaker(name = "productService", fallbackMethod = "fallbackProductService")
    public Ordering orderFeignKafkaCreate(OrderCreateDto orderDto, String userId){

        ProductDto productDto = productFeign.getProductById(orderDto.getProductId(), userId);

        int quantity = orderDto.getProductCount();

        if (productDto.getStockQuantity() < quantity) {
            throw new IllegalArgumentException("재고 부족");
        } else {

            /*productFeign.updateProductStock(
                    ProductUpdateStockDto.builder()
                            .productId(orderDto.getProductId())
                            .productQuantity(orderDto.getProductCount())
                            .build()
            );*/
            kafkaTemplate.send("update-stock-topic",
                    ProductUpdateStockDto.builder()
                            .productId(orderDto.getProductId())
                            .productQuantity(orderDto.getProductCount())
                            .build());
        }

        Ordering ordering = Ordering.builder()
                .memberId(Long.parseLong(userId))
                .productId(orderDto.getProductId())
                .quantity(orderDto.getProductCount())
                .build();
        orderingRepository.save(ordering);
        return  ordering;
    }

    public void fallbackProductService(OrderCreateDto orderCreateDto, String userId, Throwable t) {
        throw new RuntimeException("상품 서비스가 응답이 없어, 에러가 발생 했습니다. 나중에 다시 시도 해주세요.");
    }
}
