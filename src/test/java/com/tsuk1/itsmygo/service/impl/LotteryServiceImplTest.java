package com.tsuk1.itsmygo.service.impl;

import com.tsuk1.itsmygo.entity.LotteryPrize;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 加权抽奖的区间边界：weight 越大，占的 roll 区间越宽 */
class LotteryServiceImplTest {

    @Test
    void weightedPickHonorsWeightRanges() {
        LotteryPrize rare = prize("一等奖", 1);
        LotteryPrize common = prize("5 元券", 3);
        List<LotteryPrize> prizes = List.of(rare, common);

        // 总权重 4：roll=1 落在一等奖，2~4 落在 5 元券
        assertThat(LotteryServiceImpl.pickWeighted(prizes, 1)).isSameAs(rare);
        assertThat(LotteryServiceImpl.pickWeighted(prizes, 2)).isSameAs(common);
        assertThat(LotteryServiceImpl.pickWeighted(prizes, 3)).isSameAs(common);
        assertThat(LotteryServiceImpl.pickWeighted(prizes, 4)).isSameAs(common);
    }

    @Test
    void nullWeightCountsAsOne() {
        LotteryPrize first = prize("无权重", null);
        LotteryPrize second = prize("有权限", 2);
        List<LotteryPrize> prizes = List.of(first, second);

        assertThat(LotteryServiceImpl.pickWeighted(prizes, 1)).isSameAs(first);
        assertThat(LotteryServiceImpl.pickWeighted(prizes, 2)).isSameAs(second);
        assertThat(LotteryServiceImpl.pickWeighted(prizes, 3)).isSameAs(second);
    }

    private LotteryPrize prize(String name, Integer weight) {
        LotteryPrize prize = new LotteryPrize();
        prize.setName(name);
        prize.setWeight(weight);
        return prize;
    }
}
