package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.Roles;
import com.tsuk1.itsmygo.dto.LotteryDrawResponse;
import com.tsuk1.itsmygo.entity.LotteryActivity;
import com.tsuk1.itsmygo.entity.LotteryActivityStatus;
import com.tsuk1.itsmygo.entity.LotteryDrawRecord;
import com.tsuk1.itsmygo.entity.LotteryDrawResult;
import com.tsuk1.itsmygo.entity.LotteryPrize;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.LotteryActivityMapper;
import com.tsuk1.itsmygo.mapper.LotteryDrawRecordMapper;
import com.tsuk1.itsmygo.mapper.LotteryPrizeMapper;
import com.tsuk1.itsmygo.mapper.UserMapper;
import com.tsuk1.itsmygo.service.LotteryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 抽奖并发：20 个用户抢 5 份奖品，必须恰好 5 个 WIN、15 个 THANKS。
 *
 * <p>本类**不加 @Transactional**：并发线程要看到彼此提交的数据，
 * 测试库里的数据在 @AfterEach 里手动清理。加事务的话，各线程各自的事务互相看不见，
 * 就测不出真实的并发库存了。</p>
 */
@SpringBootTest
class LotteryDrawConcurrencyTest {

    private static final int USERS = 20;
    private static final int STOCK = 5;

    @Autowired
    private LotteryService lotteryService;
    @Autowired
    private LotteryActivityMapper activityMapper;
    @Autowired
    private LotteryPrizeMapper prizeMapper;
    @Autowired
    private LotteryDrawRecordMapper drawRecordMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private Long activityId;
    private Long prizeId;
    private final List<Long> userIds = new ArrayList<>();

    @Test
    void twentyUsersCompeteForFivePrizes() throws Exception {
        Long stamp = System.nanoTime();
        activityId = createActivity(stamp);
        prizeId = createPrize();
        createUsers(stamp);

        // 所有线程卡在起跑线，一声令下同时抽奖
        ExecutorService pool = Executors.newFixedThreadPool(USERS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(USERS);
        AtomicInteger win = new AtomicInteger();
        AtomicInteger thanks = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        for (Long userId : userIds) {
            pool.submit(() -> {
                try {
                    start.await();
                    LotteryDrawResponse response = lotteryService.draw(userId, activityId);
                    if (LotteryDrawResult.WIN.name().equals(response.result())) {
                        win.incrementAndGet();
                    } else {
                        thanks.incrementAndGet();
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(errors.get()).isZero();
        assertThat(win.get()).isEqualTo(STOCK);
        assertThat(thanks.get()).isEqualTo(USERS - STOCK);

        long records = drawRecordMapper.selectCount(Wrappers.<LotteryDrawRecord>lambdaQuery()
                .eq(LotteryDrawRecord::getActivityId, activityId));
        assertThat(records).isEqualTo(USERS);

        List<LotteryDrawRecord> wins = drawRecordMapper.selectList(
                Wrappers.<LotteryDrawRecord>lambdaQuery()
                        .eq(LotteryDrawRecord::getActivityId, activityId)
                        .eq(LotteryDrawRecord::getResult, LotteryDrawResult.WIN.name()));
        assertThat(wins).hasSize(STOCK);

        Set<String> codes = new HashSet<>();
        for (LotteryDrawRecord record : wins) {
            codes.add(record.getPrizeCode());
        }
        assertThat(codes).hasSize(STOCK);
    }

    private Long createActivity(Long stamp) {
        LotteryActivity activity = new LotteryActivity();
        activity.setName("并发抽奖" + stamp);
        activity.setStatus(LotteryActivityStatus.ACTIVE.code());
        activity.setStartAt(LocalDateTime.now().minusHours(1));
        activity.setEndAt(LocalDateTime.now().plusHours(1));
        activityMapper.insert(activity);
        return activity.getId();
    }

    private Long createPrize() {
        LotteryPrize prize = new LotteryPrize();
        prize.setActivityId(activityId);
        prize.setName("并发奖品");
        prize.setLevel(1);
        prize.setStock(STOCK);
        prizeMapper.insert(prize);
        return prize.getId();
    }

    private void createUsers(Long stamp) {
        for (int i = 0; i < USERS; i++) {
            User user = new User();
            user.setUsername("lottery" + stamp + "_" + i);
            user.setPassword(passwordEncoder.encode("secret123"));
            user.setNickname("lottery");
            user.setPhone("");
            user.setStatus(1);
            user.setRole(Roles.CUSTOMER);
            userMapper.insert(user);
            userIds.add(user.getId());
        }
    }

    @AfterEach
    void cleanup() {
        if (activityId != null) {
            drawRecordMapper.delete(Wrappers.<LotteryDrawRecord>lambdaQuery()
                    .eq(LotteryDrawRecord::getActivityId, activityId));
        }
        if (prizeId != null) {
            prizeMapper.deleteById(prizeId);
        }
        if (activityId != null) {
            activityMapper.deleteById(activityId);
            Set<String> keys = redisTemplate.keys("its-my-go:lottery:*" + activityId + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
        for (Long userId : userIds) {
            userMapper.deleteById(userId);
        }
        userIds.clear();
        activityId = null;
        prizeId = null;
    }
}
