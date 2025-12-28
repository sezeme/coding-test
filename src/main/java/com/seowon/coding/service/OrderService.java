package com.seowon.coding.service;

import com.seowon.coding.domain.model.Order;
import com.seowon.coding.domain.model.OrderItem;
import com.seowon.coding.domain.model.ProcessingStatus;
import com.seowon.coding.domain.model.Product;
import com.seowon.coding.domain.repository.OrderRepository;
import com.seowon.coding.domain.repository.ProcessingStatusRepository;
import com.seowon.coding.domain.repository.ProductRepository;
import com.seowon.coding.util.ListFun;
import lombok.RequiredArgsConstructor;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProcessingStatusRepository processingStatusRepository;
    
    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
    
    @Transactional(readOnly = true)
    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }
    

    public Order updateOrder(Long id, Order order) {
        if (!orderRepository.existsById(id)) {
            throw new RuntimeException("Order not found with id: " + id);
        }
        order.setId(id);
        return orderRepository.save(order);
    }
    
    public void deleteOrder(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new RuntimeException("Order not found with id: " + id);
        }
        orderRepository.deleteById(id);
    }



    public Order placeOrder(String customerName, String customerEmail, List<Long> productIds, List<Integer> quantities) {
        // TODO #3: 구현 항목
        // * 주어진 고객 정보로 새 Order를 생성
        Order newOrder = Order.builder()
                .customerName(customerName)
                .customerEmail(customerEmail)
                .build();
        // * 지정된 Product를 주문에 추가
            // product 찾기, 개수 mapping 하기
        List<Product> products = productRepository.findAllById(productIds);
        List<Pair<Product, Integer>> pairs = ListFun.zip(products, quantities);
        pairs.forEach(pair -> {
            for (int i = 1; i <= pair.getSecond(); i++) {
                OrderItem item = OrderItem.builder()
                        .order(newOrder)
                        .product(pair.getFirst())
                        .build();
                newOrder.addItem(item);
            }
        });

        // * order 의 상태를 PENDING 으로 변경
        newOrder.setStatus(Order.OrderStatus.PENDING);
        // * orderDate 를 현재시간으로 설정
        newOrder.setOrderDate(LocalDateTime.now());
        // * order 를 저장
        Order savedOrder = orderRepository.save(newOrder);
        // * 각 Product 의 재고를 수정
        pairs.forEach(pair -> {
            Product product = pair.getFirst();
            Integer quantity = pair.getSecond();
            product.decreaseStock(quantity);
        });
        // * placeOrder 메소드의 시그니처는 변경하지 않은 채 구현하세요.
        return savedOrder;
    }

    /**
     * TODO #4 (리펙토링): Service 에 몰린 도메인 로직을 도메인 객체 안으로 이동
     * - Repository 조회는 도메인 객체 밖에서 해결하여 의존 차단 합니다.
     * - #3 에서 추가한 도메인 메소드가 있을 경우 사용해도 됩니다.
     */
    public Order checkoutOrder(String customerName,
                               String customerEmail,
                               List<OrderProduct> orderProducts,
                               String couponCode) {
        if (customerName == null || customerEmail == null) {
            throw new IllegalArgumentException("customer info required");
        }
        if (orderProducts == null || orderProducts.isEmpty()) {
            throw new IllegalArgumentException("orderReqs invalid");
        }

        Order order = Order.createEntity(customerName, customerEmail);


        for (OrderProduct req : orderProducts) {
            Long pid = req.getProductId();
            int qty = req.getQuantity();

            Product product = productRepository.findById(pid)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + pid));
            if (qty <= 0) {
                throw new IllegalArgumentException("quantity must be positive: " + qty);
            }
            if (product.getStockQuantity() < qty) {
                throw new IllegalStateException("insufficient stock for product " + pid);
            }

            OrderItem item = OrderItem.createEntity(order, product, qty);
            order.addItem(item);

            product.decreaseStock(qty);
        }

        order.calculateTotalAmount(couponCode);
        order.markAsProcessing();
        return orderRepository.save(order);
    }

    /**
     * TODO #5: 코드 리뷰 - 장시간 작업과 진행률 저장의 트랜잭션 분리
     * - 시나리오: 일괄 배송 처리 중 진행률을 저장하여 다른 사용자가 조회 가능해야 함.
     * - 리뷰 포인트: proxy 및 transaction 분리, 예외 전파/롤백 범위, 가독성 등
     * - 상식적인 수준에서 요구사항(기획)을 가정하며 최대한 상세히 작성하세요.
     */
    @Transactional
    /* 너무 넓은 트랜젝션 범위
    * DB 커넥션 장시간 점유, 장애 시 롤백 비용 큼
    * -> 단일 주문 단위 메서드 분리 후 트랜잭션 */
    public void bulkShipOrdersParent(String jobId, List<Long> orderIds) {
        ProcessingStatus ps = processingStatusRepository.findByJobId(jobId)
                .orElseGet(() -> processingStatusRepository.save(ProcessingStatus.builder().jobId(jobId).build()));
        ps.markRunning(orderIds == null ? 0 : orderIds.size());
        processingStatusRepository.save(ps);

        int processed = 0;
        for (Long orderId : (orderIds == null ? List.<Long>of() : orderIds)) {
            try {
                // 오래 걸리는 작업 이라는 가정 시뮬레이션 (예: 외부 시스템 연동, 대용량 계산 등)
                orderRepository.findById(orderId).ifPresent(o -> o.setStatus(Order.OrderStatus.PROCESSING));
                // 중간 진행률 저장
                /* 트랜잭션 분리 되고 있지 않음
                * this.xxx <- 객체 내부에서 호출되어, Spring 프록시 호출되지 않고있으므로 해당 서비스 함수는 다른 클래스로 분리
                * */
                this.updateProgressRequiresNew(jobId, ++processed, orderIds.size());
            } catch (Exception e) {
                /* 실패 상태 변경 해야 함 */
                /* 실패 원인 로그 남겨야 함 */
            }
        }
        /* 같은 상태 엔티티를 여러 컨텍스트에서 만지고 있음
        * 진행 상태 엔티티는 한 트랜잭션 전략에서만 수정 -> 서비스 클래스 분리 후 Propagation.REQUIRES_NEW 로 관리 */
        ps = processingStatusRepository.findByJobId(jobId).orElse(ps);
        ps.markCompleted();
        processingStatusRepository.save(ps);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgressRequiresNew(String jobId, int processed, int total) {
        ProcessingStatus ps = processingStatusRepository.findByJobId(jobId)
                .orElseGet(() -> ProcessingStatus.builder().jobId(jobId).build());
        ps.updateProgress(processed, total);
        processingStatusRepository.save(ps);
    }

}