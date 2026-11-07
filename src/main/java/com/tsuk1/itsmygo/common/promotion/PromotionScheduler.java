package com.tsuk1.itsmygo.common.promotion;

import com.tsuk1.itsmygo.common.logging.RequestIdFilter;
import com.tsuk1.itsmygo.service.PromotionService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 智能推广每日定时：默认 10:00（Asia/Shanghai），可通过
 * {@code itsmygo.promotion.cron} 调整，{@code itsmygo.promotion.enabled=false} 关闭。
 *
 * <p>单实例低并发前提；批量上限见 {@code itsmygo.promotion.batch-size}，
 * 单用户异常由 {@link PromotionService#runDaily()} 内部捕获，不中断整批。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "itsmygo.promotion", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class PromotionScheduler {

    private final PromotionService promotionService;

    public PromotionScheduler(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @Scheduled(cron = "${itsmygo.promotion.cron:0 0 10 * * *}", zone = "Asia/Shanghai")
    public void daily() {
        MDC.put(RequestIdFilter.TRACE_ID,
                "promotion-" + UUID.randomUUID().toString().replace("-", ""));
        try {
            int sent = promotionService.runDaily();
            log.info("智能推广定时任务完成：成功发送 {} 人", sent);
        } catch (RuntimeException e) {
            log.warn("智能推广定时任务失败: {}", e.getMessage());
        } finally {
            MDC.remove(RequestIdFilter.TRACE_ID);
        }
    }
}
