package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.entity.UserFootprint;
import com.tsuk1.itsmygo.mapper.UserFootprintMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 顾客端足迹：按商品去重、倒序、只保留最近 100 条 */
class FootprintControllerTest extends ApiTestBase {

    @Autowired
    private UserFootprintMapper footprintMapper;

    private long userId(String username) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, username)).getId();
    }

    @Test
    void recordingSameProductTwiceKeepsOneRow() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String username = "f" + System.nanoTime();
        String customer = customerToken(username);
        long productId = createProductForTest(admin, "足迹商品", "10.00", 5);

        mockMvc.perform(post("/api/footprints/" + productId)
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/api/footprints/" + productId)
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/footprints")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].productId").value(productId))
                .andExpect(jsonPath("$.data.records[0].name").value("足迹商品"));
    }

    @Test
    void footprintsAreOrderedByMostRecent() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("f" + System.nanoTime());
        long first = createProductForTest(admin, "足迹A", "10.00", 5);
        long second = createProductForTest(admin, "足迹B", "10.00", 5);

        mockMvc.perform(post("/api/footprints/" + first)
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/footprints/" + second)
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/footprints")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.records[0].productId").value(second))
                .andExpect(jsonPath("$.data.records[1].productId").value(first));
    }

    /** 超过 110 条触发清理，只保留最新 100 条 */
    @Test
    void footprintTrimsToLatestHundred() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String username = "f" + System.nanoTime();
        String customer = customerToken(username);
        long userId = userId(username);
        long realProduct = createProductForTest(admin, "足迹清理", "10.00", 5);

        LocalDateTime base = LocalDateTime.now().minusDays(1);
        for (int i = 0; i < 111; i++) {
            UserFootprint row = new UserFootprint();
            row.setUserId(userId);
            row.setProductId(900_000L + i);
            row.setViewedAt(base.plusSeconds(i));
            footprintMapper.insert(row);
        }
        assertThat(footprintMapper.countByUser(userId)).isEqualTo(111);

        mockMvc.perform(post("/api/footprints/" + realProduct)
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk());

        assertThat(footprintMapper.countByUser(userId)).isEqualTo(100);
        UserFootprint newest = footprintMapper.selectOne(Wrappers.<UserFootprint>lambdaQuery()
                .eq(UserFootprint::getUserId, userId)
                .eq(UserFootprint::getProductId, realProduct));
        assertThat(newest).isNotNull();
    }

    @Test
    void clearRemovesAllFootprints() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("f" + System.nanoTime());
        long productId = createProductForTest(admin, "足迹清空", "10.00", 5);

        mockMvc.perform(post("/api/footprints/" + productId)
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/footprints")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/footprints")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void footprintRejectsMissingProduct() throws Exception {
        String customer = customerToken("f" + System.nanoTime());

        mockMvc.perform(post("/api/footprints/999999999")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void footprintRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/footprints"))
                .andExpect(status().isUnauthorized());
    }
}
