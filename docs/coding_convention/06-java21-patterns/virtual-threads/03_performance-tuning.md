# Performance Tuning - Virtual Threads 성능 최적화 및 모니터링

**목적**: Virtual Threads 환경에서 성능 병목 지점 식별 및 최적화

**관련 문서**:
- [Virtual Threads Basics](./01_virtual-threads-basics.md)
- [Async Processing](./02_async-processing.md)

**필수 버전**: Java 21+, Spring Boot 3.2+

---

## 📌 핵심 원칙

### Virtual Threads 성능 특성

1. **I/O 최적화**: I/O 대기 시 자동으로 다른 작업 실행 → 처리량 증가
2. **메모리 효율**: Platform Thread 대비 99% 메모리 절감
3. **Pinning 회피**: `synchronized` 사용 시 성능 저하
4. **CPU 작업 부적합**: CPU 집약적 작업은 ForkJoinPool 사용

---

## ⚠️ 성능 병목 지점

### 문제 1: Pinning (Platform Thread 고정)

```java
// ❌ Pinning 문제 - Virtual Thread가 Platform Thread에 고정됨
@Service
public class ProblematicService {

    private final Object lock = new Object();

    /**
     * ❌ synchronized 블록 + I/O = Pinning
     *
     * - Virtual Thread가 synchronized 진입 시 Platform Thread에 고정
     * - I/O 대기 중에도 Platform Thread 점유 → 다른 Virtual Thread 실행 불가
     * - Virtual Thread의 이점 상실
     */
    public void processOrder(OrderId orderId) {
        synchronized (lock) {  // ⚠️ Pinning 발생!
            // I/O 작업 (DB 조회)
            Order order = orderRepository.findById(orderId).orElseThrow();

            // I/O 작업 (외부 API 호출)
            PaymentInfo payment = paymentClient.getPaymentInfo(order.getPaymentId());

            // 총 250ms I/O 대기 동안 Platform Thread 점유!
            updateOrderStatus(order, payment);
        }
    }
}
```

**문제 상황**:
- Virtual Thread A가 `synchronized` 진입 → Platform Thread 1에 고정 (Pinned)
- Virtual Thread A가 I/O 대기 → Platform Thread 1은 블로킹 상태
- Virtual Thread B가 실행 대기 → Platform Thread 1 사용 불가 → 다른 Platform Thread 대기

**결과**: Virtual Thread의 경량성 이점 상실, Platform Thread처럼 동작

---

### ✅ 해결 방법: ReentrantLock 사용

```java
package com.company.application.service;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Pinning 회피 - ReentrantLock 사용
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class OptimizedService {

    private final ReentrantLock lock = new ReentrantLock();

    /**
     * ✅ ReentrantLock + I/O = Pinning 없음
     *
     * - I/O 대기 시 Virtual Thread가 Platform Thread에서 분리됨
     * - Platform Thread는 다른 Virtual Thread 실행 가능
     * - Virtual Thread의 이점 유지
     */
    public void processOrder(OrderId orderId) {
        lock.lock();
        try {
            // I/O 작업 (DB 조회) - Pinning 없음
            Order order = orderRepository.findById(orderId).orElseThrow();

            // I/O 작업 (외부 API 호출) - Pinning 없음
            PaymentInfo payment = paymentClient.getPaymentInfo(order.getPaymentId());

            // I/O 대기 중 Platform Thread는 다른 Virtual Thread 실행 가능!
            updateOrderStatus(order, payment);

        } finally {
            lock.unlock();
        }
    }
}
```

**Before (synchronized) vs After (ReentrantLock)**:

| 항목 | synchronized | ReentrantLock |
|------|--------------|---------------|
| Pinning | 발생 | 없음 |
| I/O 대기 시 | Platform Thread 블로킹 | Virtual Thread만 대기 |
| 동시성 | 낮음 (Platform Thread 수 제한) | 높음 (Virtual Thread 수 무제한) |
| 성능 | I/O 집약적 환경에서 저하 | 최적 |

---

### 문제 2: CPU 집약적 작업

```java
// ❌ Virtual Thread - CPU 집약적 작업 부적합
@Service
public class ImageProcessingService {

    /**
     * ❌ Virtual Thread에서 CPU 집약적 작업
     *
     * - Virtual Thread는 I/O 최적화 설계
     * - CPU 작업 시 Platform Thread 점유 → 다른 Virtual Thread 대기
     * - Platform Thread보다 오히려 느릴 수 있음
     */
    @Async  // Virtual Thread 사용
    public CompletableFuture<byte[]> compressImage(byte[] imageData) {
        // CPU 집약적 이미지 압축 (5초 소요)
        return CompletableFuture.completedFuture(
            ImageCompressor.compress(imageData)
        );
    }
}
```

---

### ✅ 해결 방법: ForkJoinPool 사용

```java
package com.company.application.service;

import java.util.concurrent.ForkJoinPool;

/**
 * CPU 집약적 작업 - ForkJoinPool 사용
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class OptimizedImageProcessingService {

    private final ForkJoinPool cpuIntensivePool = new ForkJoinPool(
        Runtime.getRuntime().availableProcessors()  // CPU 코어 수
    );

    /**
     * ✅ ForkJoinPool에서 CPU 집약적 작업
     *
     * - CPU 코어 수만큼 병렬 실행
     * - Work Stealing 알고리즘으로 부하 분산
     * - Virtual Thread는 I/O 작업에만 사용
     */
    public CompletableFuture<byte[]> compressImage(byte[] imageData) {
        return CompletableFuture.supplyAsync(() -> {
            // CPU 집약적 이미지 압축
            return ImageCompressor.compress(imageData);
        }, cpuIntensivePool);  // ✅ ForkJoinPool 명시
    }

    /**
     * ✅ I/O + CPU 작업 분리
     */
    public CompletableFuture<ImageResult> processImagePipeline(ImageId imageId) {
        // 1. I/O 작업: S3에서 이미지 다운로드 (Virtual Thread)
        return downloadImageAsync(imageId)  // @Async → Virtual Thread
            // 2. CPU 작업: 이미지 압축 (ForkJoinPool)
            .thenComposeAsync(imageData ->
                compressImage(imageData), cpuIntensivePool)
            // 3. I/O 작업: S3에 업로드 (Virtual Thread)
            .thenComposeAsync(compressed ->
                uploadImageAsync(compressed));  // @Async → Virtual Thread
    }
}
```

**작업 유형별 최적 실행 환경**:

| 작업 유형 | 최적 실행 환경 | 이유 |
|----------|--------------|------|
| I/O 집약적 (DB, API, 파일) | Virtual Thread | I/O 대기 시 자동 양보 |
| CPU 집약적 (암호화, 압축) | ForkJoinPool | Work Stealing, CPU 코어 활용 |
| 짧은 CPU 작업 (<10ms) | Virtual Thread | 오버헤드 적음 |
| 긴 CPU 작업 (>100ms) | ForkJoinPool | Platform Thread 점유 방지 |

---

## 📊 성능 측정 및 모니터링

### 패턴 1: JFR (Java Flight Recorder) 이벤트

```java
package com.company.application.monitoring;

import jdk.jfr.*;

/**
 * Virtual Thread 성능 모니터링 - JFR 이벤트
 *
 * @author development-team
 * @since 1.0.0
 */
@Name("com.company.VirtualThreadTask")
@Label("Virtual Thread Task")
@Category("Application")
public class VirtualThreadTaskEvent extends Event {

    @Label("Task Name")
    public String taskName;

    @Label("Duration")
    @Timespan(Timespan.MILLISECONDS)
    public long duration;

    @Label("Thread Type")
    public String threadType;

    /**
     * ✅ Virtual Thread 작업 성능 측정
     */
    public static void recordTask(String taskName, Runnable task) {
        VirtualThreadTaskEvent event = new VirtualThreadTaskEvent();
        event.taskName = taskName;
        event.threadType = Thread.currentThread().isVirtual() ? "Virtual" : "Platform";

        event.begin();
        long startTime = System.currentTimeMillis();

        try {
            task.run();
        } finally {
            event.duration = System.currentTimeMillis() - startTime;
            event.commit();
        }
    }
}
```

**JFR 분석**:
```bash
# JFR 기록 시작
jcmd <pid> JFR.start name=virtual-threads settings=profile

# JFR 덤프
jcmd <pid> JFR.dump name=virtual-threads filename=recording.jfr

# JFR 분석 (JDK Mission Control 사용)
jmc recording.jfr
```

---

### 패턴 2: Micrometer Metrics

```java
package com.company.application.monitoring;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

/**
 * Virtual Thread Metrics - Micrometer
 *
 * @author development-team
 * @since 1.0.0
 */
@Component
public class VirtualThreadMetrics {

    private final Counter virtualThreadTaskCounter;
    private final Timer virtualThreadTaskTimer;
    private final Gauge virtualThreadCount;

    public VirtualThreadMetrics(MeterRegistry registry) {
        // ✅ Virtual Thread 작업 카운터
        this.virtualThreadTaskCounter = Counter.builder("virtual.thread.tasks")
            .description("Total number of Virtual Thread tasks executed")
            .tag("type", "async")
            .register(registry);

        // ✅ Virtual Thread 작업 실행 시간
        this.virtualThreadTaskTimer = Timer.builder("virtual.thread.task.duration")
            .description("Virtual Thread task execution time")
            .register(registry);

        // ✅ 현재 실행 중인 Virtual Thread 수
        this.virtualThreadCount = Gauge.builder("virtual.thread.count", this, value -> {
            ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
            while (rootGroup.getParent() != null) {
                rootGroup = rootGroup.getParent();
            }
            Thread[] threads = new Thread[rootGroup.activeCount()];
            rootGroup.enumerate(threads);
            return Arrays.stream(threads)
                .filter(Thread::isVirtual)
                .count();
        }).register(registry);
    }

    /**
     * ✅ 작업 실행 시간 측정
     */
    public <T> T recordTask(String taskName, Callable<T> task) throws Exception {
        return virtualThreadTaskTimer.recordCallable(() -> {
            virtualThreadTaskCounter.increment();
            return task.call();
        });
    }
}
```

---

### 패턴 3: Pinning 감지

```java
/**
 * Pinning 감지 및 경고
 *
 * @author development-team
 * @since 1.0.0
 */
@Component
public class PinningDetector {

    private static final Logger log = LoggerFactory.getLogger(PinningDetector.class);

    /**
     * ✅ JVM 옵션으로 Pinning 감지
     *
     * JVM 실행 시 추가:
     * -Djdk.tracePinnedThreads=full
     *
     * Pinning 발생 시 스택 트레이스 출력
     */
    public void enablePinningDetection() {
        System.setProperty("jdk.tracePinnedThreads", "full");
    }

    /**
     * ✅ 프로그래밍 방식으로 Pinning 감지
     */
    public void detectPinning(Runnable task) {
        Thread currentThread = Thread.currentThread();

        if (currentThread.isVirtual()) {
            long startCarrierThreadId = getCarrierThreadId(currentThread);

            task.run();

            long endCarrierThreadId = getCarrierThreadId(currentThread);

            // Pinning 감지: Carrier Thread가 변경되지 않음
            if (startCarrierThreadId == endCarrierThreadId) {
                log.warn("Potential pinning detected in Virtual Thread: {}",
                    Thread.currentThread().getName());
            }
        } else {
            task.run();
        }
    }

    private long getCarrierThreadId(Thread virtualThread) {
        // Java 내부 API 사용 (실제 구현은 리플렉션 필요)
        return 0L; // 간소화된 예시
    }
}
```

**Pinning 로그 예시**:
```
Thread[#23,ForkJoinPool-1-worker-1,5,CarrierThreads]
    java.base/java.lang.VirtualThread$VThreadContinuation.onPinned(VirtualThread.java:180)
    com.company.service.ProblematicService.processOrder(ProblematicService.java:15)
        <== monitors:1
```

---

## 🔧 성능 최적화 체크리스트

### Pinning 회피
- [ ] `synchronized` 대신 `ReentrantLock` 사용
- [ ] `synchronized` 블록 내 I/O 작업 제거
- [ ] JVM 옵션 `-Djdk.tracePinnedThreads=full` 활성화

### 작업 분리
- [ ] I/O 작업 → Virtual Thread
- [ ] CPU 작업 → ForkJoinPool
- [ ] 짧은 CPU 작업 (<10ms) → Virtual Thread 허용

### 모니터링
- [ ] JFR 이벤트 기록
- [ ] Micrometer Metrics 수집
- [ ] Pinning 감지 및 경고

### 리소스 관리
- [ ] `StructuredTaskScope` try-with-resources 사용
- [ ] ThreadLocal 대신 ScopedValue 고려
- [ ] 불필요한 Thread 생성 최소화

---

## 📈 성능 벤치마크

### Before (Platform Thread) vs After (Virtual Thread)

**테스트 시나리오**: 10,000개 요청, 각 요청당 200ms I/O 대기

| 메트릭 | Platform Thread (200개) | Virtual Thread |
|--------|------------------------|----------------|
| 총 처리 시간 | 10초 (50 라운드) | 200ms (1 라운드) |
| 메모리 사용량 | 400MB (2MB × 200) | 10MB (1KB × 10,000) |
| 스레드 생성 비용 | 높음 (200개 재사용) | 낮음 (10,000개 생성) |
| CPU 사용률 | 10% (I/O 대기) | 10% (I/O 대기) |
| 처리량 (RPS) | 2,000 | 50,000 (25배 향상) |

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
