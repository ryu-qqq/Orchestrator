# Transaction Boundaries with External Calls

**Issue**: [#28](https://github.com/ryu-qqq/claude-spring-standards/issues/28)
**Priority**: 🔴 CRITICAL
**Validation**: `hooks/validators/transaction-boundary-validator.sh`

---

## 📋 핵심 원칙

외부 API 호출은 `@Transactional` 메서드 **밖**에 배치해야 합니다.

### 외부 호출의 정의
다음과 같은 I/O 작업은 모두 외부 호출로 간주합니다:

- ☁️ **AWS 서비스**: S3, SQS, SNS, DynamoDB
- 🌐 **HTTP/REST API**: RestTemplate, WebClient, FeignClient
- 📬 **Message Queue**: RabbitMQ, Kafka, Redis Pub/Sub
- 📧 **이메일/SMS**: JavaMail, AWS SES, Twilio
- 💳 **외부 결제**: PG사 API, 결제 게이트웨이

---

## 🚨 문제점: 외부 API가 트랜잭션 내부에 있으면

### 성능 문제
```
외부 API 평균 응답 시간: 100-500ms
DB 작업 평균 시간: 10-50ms

트랜잭션 내 외부 API 호출 시:
→ DB 커넥션 점유 시간: 500ms (외부 API 대기)
→ 커넥션 풀 고갈 위험 증가
```

### 구체적 문제
1. **DB 커넥션 장기 점유**
   - 외부 API 응답 대기 중 커넥션 Lock
   - 다른 요청의 DB 접근 지연

2. **커넥션 풀 고갈**
   - 동시 요청 100개 시 커넥션 풀(10개) 전부 점유
   - 신규 요청 대기 or 타임아웃

3. **트랜잭션 타임아웃**
   - 외부 API 장애 시 트랜잭션 타임아웃 발생
   - DB 작업도 롤백 (불필요한 실패)

4. **장애 전파**
   - S3 장애 → DB 트랜잭션 실패
   - 외부 시스템 장애가 내부 시스템까지 영향

---

## ❌ Bad - 외부 호출이 트랜잭션 내부

```java
@Service
public class UploadSessionService {

    @Transactional
    public UploadSessionWithUrlResponse createSession(CreateSessionCommand command) {
        // 1. 정책 검증 (메모리 작업 - OK)
        UploadPolicy policy = validateUploadPolicy(command);

        // 2. 도메인 객체 생성 (메모리 작업 - OK)
        UploadSession session = UploadSession.create(
            policy,
            command.userId(),
            command.fileSize()
        );

        // ❌ 3. S3 Presigned URL 발급 (외부 API - 트랜잭션 내부!)
        //    문제점:
        //    - 네트워크 I/O로 인한 지연 (100-500ms)
        //    - DB 커넥션을 불필요하게 점유
        //    - S3 장애 시 DB 트랜잭션까지 실패
        PresignedUrlInfo presignedUrlInfo = generatePresignedUrlPort.generate(
            session.getSessionId(),
            command.fileName()
        );

        // 4. DB 저장 (트랜잭션 필요 - OK)
        UploadSession savedSession = uploadSessionPort.save(session);

        return new UploadSessionWithUrlResponse(savedSession, presignedUrlInfo);
    }
}
```

### 문제 분석
- **트랜잭션 시간**: ~500ms (DB 50ms + S3 400ms + 여유 50ms)
- **DB 커넥션 점유**: 500ms
- **동시 100 요청 시**: 커넥션 풀(10개) 고갈 확률 높음
- **S3 장애 시**: DB 작업도 롤백 (불필요한 실패)

---

## ✅ Good - 외부 호출과 DB 작업 분리

### 패턴 1: 외부 API 먼저 → DB 작업 나중

```java
@Service
public class UploadSessionService {
    private final UploadSessionPersistenceService persistenceService;
    private final GeneratePresignedUrlPort generatePresignedUrlPort;

    // ✅ @Transactional 제거 - 외부 API 호출 포함
    public UploadSessionWithUrlResponse createSession(CreateSessionCommand command) {
        // 1. 정책 검증 (메모리 작업)
        UploadPolicy policy = validateUploadPolicy(command);

        // 2. 도메인 객체 생성 (메모리 작업)
        UploadSession session = UploadSession.create(
            policy,
            command.userId(),
            command.fileSize()
        );

        // ✅ 3. S3 Presigned URL 발급 (외부 API - 트랜잭션 밖!)
        //    장점:
        //    - DB 커넥션 점유 없음
        //    - S3 장애와 DB 작업 분리
        //    - 실패 시 DB 작업 시작하지 않음
        PresignedUrlInfo presignedUrlInfo;
        try {
            presignedUrlInfo = generatePresignedUrlPort.generate(
                session.getSessionId(),
                command.fileName()
            );
        } catch (S3Exception e) {
            throw new PresignedUrlGenerationException(
                "Failed to generate presigned URL for session: " + session.getSessionId(),
                e
            );
        }

        // ✅ 4. DB 저장 (별도 트랜잭션 - 빠른 커밋)
        //    - persistenceService 내부에서 @Transactional 적용
        //    - 외부 API 호출 없이 빠르게 커밋 (10-50ms)
        UploadSession savedSession = persistenceService.saveSession(session);

        return new UploadSessionWithUrlResponse(savedSession, presignedUrlInfo);
    }
}

/**
 * DB 작업만 담당하는 별도 Service
 */
@Service
public class UploadSessionPersistenceService {
    private final SaveUploadSessionPort uploadSessionPort;

    // ✅ 외부 API 호출 없는 순수 DB 작업만 포함
    @Transactional
    public UploadSession saveSession(UploadSession session) {
        if (session == null) {
            throw new IllegalArgumentException("UploadSession must not be null");
        }
        return uploadSessionPort.save(session);
    }
}
```

### 성능 개선 효과
- **Before**: 트랜잭션 시간 ~500ms (DB 50ms + S3 400ms)
- **After**: 트랜잭션 시간 ~50ms (DB 작업만)
- **DB 커넥션 점유 시간**: 90% 감소 (500ms → 50ms)
- **커넥션 풀 고갈 위험**: 해소 (동시 100 요청 처리 가능)

---

## 📊 트랜잭션 분리 전략

### 전략 1: 외부 호출 → DB 작업
**사용 시점**: 외부 API 실패 시 DB 작업 시작하지 않아야 할 때

```java
public Result process(Command cmd) {
    // 1. 외부 호출 먼저 (트랜잭션 밖)
    ExternalResult externalResult = externalApi.call();

    // 2. DB 작업 (별도 트랜잭션)
    //    외부 API 실패 시 여기까지 오지 않음
    return persistenceService.save(externalResult);
}
```

**장점**:
- 외부 API 실패 시 DB 작업 자체를 하지 않음
- 불필요한 DB 작업 방지

**단점**:
- DB 작업 실패 시 외부 API 결과 롤백 불가
- 보상 트랜잭션 필요할 수 있음

---

### 전략 2: DB 작업 → 외부 호출 (이벤트 기반)
**사용 시점**: DB 작업 먼저 확정해야 하고, 외부 호출 실패 허용 가능

```java
@Service
public class OrderService {

    // ✅ DB 작업만 포함 (트랜잭션)
    @Transactional
    public Order createOrder(CreateOrderCommand command) {
        Order order = Order.create(command.userId(), command.items());
        Order savedOrder = orderRepository.save(order);

        // ✅ 이벤트 발행 (트랜잭션 커밋 후 비동기 처리)
        eventPublisher.publishEvent(new OrderCreatedEvent(savedOrder.getId()));

        return savedOrder;
    }
}

/**
 * 비동기 이벤트 핸들러 (트랜잭션 밖)
 */
@Component
public class OrderEventHandler {

    @EventListener
    @Async
    public void handleOrderCreated(OrderCreatedEvent event) {
        // ✅ 외부 API 호출 (트랜잭션 밖)
        //    - DB 작업은 이미 완료됨
        //    - 실패해도 DB 작업은 유지
        try {
            emailService.sendOrderConfirmation(event.getOrderId());
            smsService.sendOrderNotification(event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to send order notification", e);
            // 실패해도 DB 작업은 유지됨
            // 재시도 로직 또는 Dead Letter Queue 처리
        }
    }
}
```

**장점**:
- DB 작업은 확정적으로 완료
- 외부 API 실패가 DB에 영향 없음
- 비동기 처리로 응답 속도 향상

**단점**:
- 외부 API 실패 시 재시도 로직 필요
- 최종 일관성 (Eventual Consistency) 모델

---

### 전략 3: 보상 트랜잭션 (Saga 패턴)
**사용 시점**: 외부 API 먼저 호출하되, DB 실패 시 외부 작업 롤백 필요

```java
public Result process(Command cmd) {
    // 1. 외부 호출 먼저 (트랜잭션 밖)
    ExternalResult externalResult = externalApi.call();

    // 2. DB 저장 시도 (별도 트랜잭션)
    try {
        return persistenceService.save(externalResult);
    } catch (Exception e) {
        // ✅ 3. 외부 작업 롤백 (보상 트랜잭션)
        try {
            externalApi.rollback(externalResult);
        } catch (Exception rollbackException) {
            log.error("Compensation failed", rollbackException);
            // Dead Letter Queue 또는 수동 보정 필요
        }
        throw e;
    }
}
```

**장점**:
- 외부 API와 DB 작업의 일관성 유지
- 롤백 가능

**단점**:
- 복잡도 증가
- 보상 트랜잭션 구현 필요
- 보상 실패 시 수동 개입 필요

---

## 🔍 외부 API 호출 식별 기준

다음 패턴들은 모두 외부 API 호출로 간주하여 `@Transactional` 밖에 배치:

### AWS SDK 호출
```java
// ❌ 트랜잭션 내 금지
s3Client.putObject(...)
s3Client.generatePresignedUrl(...)
sqsClient.sendMessage(...)
snsClient.publish(...)
dynamoDbClient.putItem(...)
```

### HTTP Client 호출
```java
// ❌ 트랜잭션 내 금지
restTemplate.getForObject(...)
restTemplate.postForEntity(...)
webClient.get().retrieve().block()
feignClient.getUserInfo(...)
```

### Message Queue 발행
```java
// ❌ 트랜잭션 내 금지
rabbitTemplate.convertAndSend(...)
kafkaTemplate.send(...)
redisTemplate.convertAndSend(...)
```

### 이메일/SMS 발송
```java
// ❌ 트랜잭션 내 금지
javaMailSender.send(...)
sesClient.sendEmail(...)
twilioClient.sendSms(...)
```

---

## ✅ 체크리스트

코드 작성 전:
- [ ] `@Transactional` 메서드에 S3/SQS/SNS 호출 없음
- [ ] `@Transactional` 메서드에 HTTP/REST 호출 없음
- [ ] `@Transactional` 메서드에 Message Queue 발행 없음
- [ ] 외부 API 실패와 DB 트랜잭션이 독립적으로 처리됨
- [ ] DB 작업만 포함한 메서드는 별도 Service로 분리
- [ ] 트랜잭션 시간이 100ms 이내 (외부 호출 제외)

커밋 전:
- [ ] Pre-commit Hook 통과 (`transaction-boundary-validator.sh`)
- [ ] ArchUnit 테스트 통과 (`TransactionArchitectureTest.java`)

---

## 🔧 검증 방법

### Git Pre-commit Hook
```bash
./hooks/validators/transaction-boundary-validator.sh
```

**검증 항목**:
- `@Transactional` 메서드 내 AWS SDK 호출 감지
- `@Transactional` 메서드 내 HTTP Client 호출 감지
- `@Transactional` 메서드 내 Message Queue 발행 감지

### ArchUnit 테스트
```java
// application/src/test/java/architecture/TransactionArchitectureTest.java
@ArchTest
static final ArchRule transactional_methods_should_not_call_external_apis =
    methods()
        .that().areAnnotatedWith(Transactional.class)
        .should().notCallMethodWhere(/* 외부 API 패턴 */)
        .because("@Transactional methods must not call external APIs to prevent long DB connection holding");
```

---

## 📚 관련 가이드

**전제 조건**:
- [Spring Proxy Limitations](./02_spring-proxy-limitations.md) - 프록시 작동 원리 이해 필수

**연관 패턴**:
- [Transaction Best Practices](./03_transaction-best-practices.md) - Aggregate 단위 트랜잭션
- [Async Processing](../../08-enterprise-patterns/async-processing/) - 비동기 처리 대안

**심화 학습**:
- [Saga Patterns](../../08-enterprise-patterns/saga-patterns/) - 보상 트랜잭션 구현

---

**Issue**: [#28](https://github.com/ryu-qqq/claude-spring-standards/issues/28)
**작성일**: 2025-10-16
**검증 도구**: `hooks/validators/transaction-boundary-validator.sh`, `TransactionArchitectureTest.java`
