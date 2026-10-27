package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.ratelimit.RateLimit;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ProductAnswerRequest;
import com.tsuk1.itsmygo.dto.ProductAnswerView;
import com.tsuk1.itsmygo.dto.ProductQuestionRequest;
import com.tsuk1.itsmygo.dto.ProductQuestionView;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.ProductQaService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品问答（问大家）。
 *
 * 读接口挂在公开的 `/api/products/**` 下；提问和回答是 POST，按 SecurityConfig
 * 的 anyRequest().authenticated() 要求登录。
 */
@RestController
@RequestMapping("/api/products/{productId}/questions")
public class ProductQaController {

    private final ProductQaService qaService;

    public ProductQaController(ProductQaService qaService) {
        this.qaService = qaService;
    }

    @GetMapping
    public Result<PageResponse<ProductQuestionView>> questions(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "all") String filter) {
        return Result.ok(qaService.questions(productId, page, size, filter));
    }

    /** 任何登录用户都可以提问；提问不进入商品评分 */
    @RateLimit(scene = "product-question", limit = 5, windowSeconds = 60, byUser = true)
    @PostMapping
    public Result<Void> ask(@AuthenticationPrincipal CurrentUser user,
                            @PathVariable Long productId,
                            @Valid @RequestBody ProductQuestionRequest request) {
        qaService.ask(user.id(), productId, request);
        return Result.ok();
    }

    @GetMapping("/{questionId}/answers")
    public Result<PageResponse<ProductAnswerView>> answers(
            @PathVariable Long productId,
            @PathVariable Long questionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size) {
        return Result.ok(qaService.answers(productId, questionId, page, size));
    }

    /** 只有买过该商品（PAID/SHIPPED/COMPLETED）的登录用户可以回答 */
    @RateLimit(scene = "product-answer", limit = 10, windowSeconds = 60, byUser = true)
    @PostMapping("/{questionId}/answers")
    public Result<Void> answer(@AuthenticationPrincipal CurrentUser user,
                               @PathVariable Long productId,
                               @PathVariable Long questionId,
                               @Valid @RequestBody ProductAnswerRequest request) {
        qaService.answer(user.id(), productId, questionId, request);
        return Result.ok();
    }
}
