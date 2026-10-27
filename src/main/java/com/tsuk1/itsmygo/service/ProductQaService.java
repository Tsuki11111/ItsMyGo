package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ProductAnswerRequest;
import com.tsuk1.itsmygo.dto.ProductAnswerView;
import com.tsuk1.itsmygo.dto.ProductQuestionRequest;
import com.tsuk1.itsmygo.dto.ProductQuestionView;

/** 商品问答（问大家），独立于买家评分评价 */
public interface ProductQaService {

    void ask(Long userId, Long productId, ProductQuestionRequest request);

    PageResponse<ProductQuestionView> questions(Long productId, int page, int size, String filter);

    PageResponse<ProductAnswerView> answers(Long productId, Long questionId, int page, int size);

    void answer(Long userId, Long productId, Long questionId, ProductAnswerRequest request);
}
