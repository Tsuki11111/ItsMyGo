package com.tsuk1.itsmygo.common.lottery;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.entity.Activity;
import com.tsuk1.itsmygo.entity.LotteryDrawRecord;
import com.tsuk1.itsmygo.entity.LotteryDrawResult;
import com.tsuk1.itsmygo.entity.LotteryPrize;
import com.tsuk1.itsmygo.mapper.LotteryDrawRecordMapper;
import com.tsuk1.itsmygo.mapper.LotteryPrizeMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 奖池初始化：把 MySQL 里配置的奖项库存，灌成 Redis 的奖券码 Set。
 *
 * <p>奖券码格式固定为 {@code LOT-{prizeId}-{seq}}。初始化时先查 MySQL 里已经中出的码，
 * 只补没发过的 —— 这样 Redis 丢失后重建也不会把同一个奖券码发两次。</p>
 *
 * <p>初始化用「活动级 Redis 锁 + 标记」保护：多个请求同时首次抽奖时，只允许一个请求灌奖池，
 * 其他请求等标记出现后再继续，避免并发 SADD 把已抽走的奖券又加回去。</p>
 */
@Service
public class LotteryPrizePoolService {

    /** 一次 SADD 不要塞太多参数，避免单条 Redis 命令过大 */
    private static final int SEED_BATCH_SIZE = 500;
    private static final Duration INIT_LOCK_TTL = Duration.ofSeconds(10);
    private static final long WAIT_INTERVAL_MILLIS = 50;
    private static final int MAX_WAIT_ROUNDS = 20;

    private final LotteryPrizeMapper prizeMapper;
    private final LotteryDrawRecordMapper drawRecordMapper;
    private final LotteryRedisStore redisStore;

    public LotteryPrizePoolService(LotteryPrizeMapper prizeMapper,
                                   LotteryDrawRecordMapper drawRecordMapper,
                                   LotteryRedisStore redisStore) {
        this.prizeMapper = prizeMapper;
        this.drawRecordMapper = drawRecordMapper;
        this.redisStore = redisStore;
    }

    /** 初始化一个活动下所有奖项的奖池；已全部初始化则直接返回 */
    public void ensureInitialized(Activity activity) {
        List<LotteryPrize> prizes = prizeMapper.selectList(Wrappers.<LotteryPrize>lambdaQuery()
                .eq(LotteryPrize::getActivityId, activity.getId()));
        if (allInitialized(activity, prizes)) {
            return;
        }

        if (!redisStore.tryInitLock(activity.getId(), INIT_LOCK_TTL)) {
            waitForOtherInitializer(activity, prizes);
            return;
        }
        try {
            // 拿到锁后双重检查：等锁期间可能已经有人初始化完了
            for (LotteryPrize prize : prizes) {
                if (!redisStore.isPrizePoolInitialized(activity.getId(), prize.getId())) {
                    initializeOne(activity, prize);
                }
            }
        } finally {
            redisStore.releaseInitLock(activity.getId());
        }
    }

    /** 实时剩余库存：确保奖池已初始化后读 SCARD */
    public long remainingStock(Activity activity, LotteryPrize prize) {
        ensureInitialized(activity);
        return redisStore.remainingStock(activity.getId(), prize.getId());
    }

    /**
     * 管理端调库存后同步 Redis 奖池（差量调整，不重建池）。
     *
     * <p>奖券码是 {@code LOT-{prizeId}-{seq}}，seq 上限就是库存；所以加库存 = 追加新序号的码，
     * 减库存 = 从池里移走几个未发出去的码（已发的码在抽中时就已出池，不会被误删）。
     * 池还没初始化过则什么都不用做 —— 下次初始化会直接按新库存灌。</p>
     *
     * @throws BizException 新库存小于已中出数量时
     */
    public void adjustStock(Activity activity, LotteryPrize prize, int oldStock, int newStock) {
        if (newStock == oldStock) {
            return;
        }
        if (newStock < oldStock) {
            long issued = drawRecordMapper.selectCount(Wrappers.<LotteryDrawRecord>lambdaQuery()
                    .eq(LotteryDrawRecord::getActivityId, activity.getId())
                    .eq(LotteryDrawRecord::getPrizeId, prize.getId())
                    .eq(LotteryDrawRecord::getResult, LotteryDrawResult.WIN.name()));
            if (newStock < issued) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "库存不能小于已中出数量（已中出 " + issued + " 件）");
            }
        }
        if (!redisStore.isPrizePoolInitialized(activity.getId(), prize.getId())) {
            return;
        }

        long ttlSeconds = redisStore.ttlSeconds(activity.getEndAt());
        if (newStock > oldStock) {
            List<String> codes = new ArrayList<>();
            for (int seq = oldStock + 1; seq <= newStock; seq++) {
                codes.add(prizeCode(prize.getId(), seq));
            }
            for (int from = 0; from < codes.size(); from += SEED_BATCH_SIZE) {
                int to = Math.min(from + SEED_BATCH_SIZE, codes.size());
                redisStore.seedPrizeCodes(activity.getId(), prize.getId(),
                        codes.subList(from, to), ttlSeconds);
            }
        } else {
            redisStore.takePrizes(activity.getId(), prize.getId(), oldStock - newStock);
        }
    }

    /** 删除奖项后清掉它的奖池与初始化标记 */
    public void removePrizePool(Activity activity, LotteryPrize prize) {
        redisStore.removePrizePool(activity.getId(), prize.getId());
    }

    private void initializeOne(Activity activity, LotteryPrize prize) {
        long ttlSeconds = redisStore.ttlSeconds(activity.getEndAt());

        // 已发出去的奖券码不再灌回池子，避免 Redis 丢失后重复发奖
        List<LotteryDrawRecord> wins = drawRecordMapper.selectList(
                Wrappers.<LotteryDrawRecord>lambdaQuery()
                        .select(LotteryDrawRecord::getPrizeCode)
                        .eq(LotteryDrawRecord::getActivityId, activity.getId())
                        .eq(LotteryDrawRecord::getPrizeId, prize.getId())
                        .eq(LotteryDrawRecord::getResult, LotteryDrawResult.WIN.name()));
        Set<String> issued = new HashSet<>();
        for (LotteryDrawRecord win : wins) {
            if (win.getPrizeCode() != null) {
                issued.add(win.getPrizeCode());
            }
        }

        List<String> codes = new ArrayList<>();
        for (int seq = 1; seq <= prize.getStock(); seq++) {
            String code = prizeCode(prize.getId(), seq);
            if (!issued.contains(code)) {
                codes.add(code);
            }
        }
        for (int from = 0; from < codes.size(); from += SEED_BATCH_SIZE) {
            int to = Math.min(from + SEED_BATCH_SIZE, codes.size());
            redisStore.seedPrizeCodes(activity.getId(), prize.getId(),
                    codes.subList(from, to), ttlSeconds);
        }

        redisStore.markPrizePoolInitialized(activity.getId(), prize.getId(), ttlSeconds);
    }

    private boolean allInitialized(Activity activity, List<LotteryPrize> prizes) {
        for (LotteryPrize prize : prizes) {
            if (!redisStore.isPrizePoolInitialized(activity.getId(), prize.getId())) {
                return false;
            }
        }
        return true;
    }

    private void waitForOtherInitializer(Activity activity, List<LotteryPrize> prizes) {
        for (int round = 0; round < MAX_WAIT_ROUNDS; round++) {
            sleep();
            if (allInitialized(activity, prizes)) {
                return;
            }
        }
        // 初始化通常只有几毫秒；等 1 秒还没好，说明持锁请求异常，让用户稍后重试
        throw new BizException(ResultCode.BAD_REQUEST, "抽奖初始化中，请稍后重试");
    }

    private String prizeCode(Long prizeId, int seq) {
        return "LOT-" + prizeId + "-" + seq;
    }

    private void sleep() {
        try {
            Thread.sleep(WAIT_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
