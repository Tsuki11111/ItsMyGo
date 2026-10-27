package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ProductAnswerRequest;
import com.tsuk1.itsmygo.dto.ProductAnswerView;
import com.tsuk1.itsmygo.dto.ProductQuestionRequest;
import com.tsuk1.itsmygo.dto.ProductQuestionView;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.ProductAnswer;
import com.tsuk1.itsmygo.entity.ProductQuestion;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.OrderItemMapper;
import com.tsuk1.itsmygo.mapper.ProductAnswerMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.ProductQuestionMapper;
import com.tsuk1.itsmygo.mapper.UserMapper;
import com.tsuk1.itsmygo.service.ProductQaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductQaServiceImpl implements ProductQaService {

    private static final int VISIBLE = 1;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int PREVIEW_ANSWERS = 2;

    private final ProductMapper productMapper;
    private final ProductQuestionMapper questionMapper;
    private final ProductAnswerMapper answerMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserMapper userMapper;

    public ProductQaServiceImpl(ProductMapper productMapper,
                                ProductQuestionMapper questionMapper,
                                ProductAnswerMapper answerMapper,
                                OrderItemMapper orderItemMapper,
                                UserMapper userMapper) {
        this.productMapper = productMapper;
        this.questionMapper = questionMapper;
        this.answerMapper = answerMapper;
        this.orderItemMapper = orderItemMapper;
        this.userMapper = userMapper;
    }

    @Override
    public void ask(Long userId, Long productId, ProductQuestionRequest request) {
        requireProduct(productId);
        ProductQuestion question = new ProductQuestion();
        question.setProductId(productId);
        question.setUserId(userId);
        question.setContent(request.content().trim());
        question.setAnswerCount(0);
        question.setStatus(VISIBLE);
        questionMapper.insert(question);
    }

    @Override
    public PageResponse<ProductQuestionView> questions(Long productId, int page, int size,
                                                       String filter) {
        requireProduct(productId);
        var query = Wrappers.<ProductQuestion>lambdaQuery()
                .eq(ProductQuestion::getProductId, productId)
                .eq(ProductQuestion::getStatus, VISIBLE);
        if ("unanswered".equals(filter)) {
            query.eq(ProductQuestion::getAnswerCount, 0);
        } else if ("answered".equals(filter)) {
            query.gt(ProductQuestion::getAnswerCount, 0);
        }
        query.orderByDesc(ProductQuestion::getId);

        Page<ProductQuestion> result = questionMapper.selectPage(
                new Page<>(normalizePage(page), normalizeSize(size)), query);
        List<ProductQuestion> rows = result.getRecords();
        if (rows.isEmpty()) {
            return new PageResponse<>(List.of(), result.getTotal(), result.getCurrent(), result.getSize());
        }

        List<Long> questionIds = rows.stream().map(ProductQuestion::getId).toList();
        List<ProductAnswer> previews = answerMapper.selectPreviewAnswers(questionIds, PREVIEW_ANSWERS);
        Map<Long, List<ProductAnswer>> answersByQuestion = previews.stream()
                .collect(Collectors.groupingBy(ProductAnswer::getQuestionId));

        List<Long> userIds = new ArrayList<>();
        rows.forEach(row -> userIds.add(row.getUserId()));
        previews.forEach(answer -> userIds.add(answer.getUserId()));
        Map<Long, User> users = loadUsers(userIds);

        List<ProductQuestionView> views = rows.stream().map(row -> {
            User asker = users.get(row.getUserId());
            List<ProductAnswerView> answers = answersByQuestion
                    .getOrDefault(row.getId(), List.of()).stream()
                    .map(answer -> answerView(answer, users.get(answer.getUserId())))
                    .toList();
            return new ProductQuestionView(
                    row.getId(), row.getContent(), nickname(asker), avatar(asker),
                    row.getAnswerCount() == null ? 0 : row.getAnswerCount(),
                    row.getCreatedAt(), answers);
        }).toList();
        return new PageResponse<>(views, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public PageResponse<ProductAnswerView> answers(Long productId, Long questionId,
                                                   int page, int size) {
        ProductQuestion question = requireQuestion(productId, questionId);
        if (!Integer.valueOf(VISIBLE).equals(question.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND, "提问不存在");
        }
        Page<ProductAnswer> result = answerMapper.selectPage(
                new Page<>(normalizePage(page), normalizeSize(size)),
                Wrappers.<ProductAnswer>lambdaQuery()
                        .eq(ProductAnswer::getQuestionId, questionId)
                        .eq(ProductAnswer::getStatus, VISIBLE)
                        .orderByDesc(ProductAnswer::getId));
        Map<Long, User> users = loadUsers(
                result.getRecords().stream().map(ProductAnswer::getUserId).toList());
        List<ProductAnswerView> views = result.getRecords().stream()
                .map(answer -> answerView(answer, users.get(answer.getUserId())))
                .toList();
        return new PageResponse<>(views, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @Transactional
    public void answer(Long userId, Long productId, Long questionId,
                       ProductAnswerRequest request) {
        ProductQuestion question = requireQuestion(productId, questionId);
        if (!Integer.valueOf(VISIBLE).equals(question.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND, "提问不存在");
        }
        if (orderItemMapper.countPurchasedProduct(userId, productId) == 0) {
            throw new BizException(ResultCode.FORBIDDEN, "只有购买过该商品的用户可以回答");
        }
        ProductAnswer answer = new ProductAnswer();
        answer.setQuestionId(questionId);
        answer.setUserId(userId);
        answer.setContent(request.content().trim());
        answer.setStatus(VISIBLE);
        answerMapper.insert(answer);
        if (questionMapper.incrementAnswerCount(questionId) == 0) {
            throw new BizException(ResultCode.NOT_FOUND, "提问不存在");
        }
    }

    private ProductAnswerView answerView(ProductAnswer answer, User user) {
        return new ProductAnswerView(answer.getId(), answer.getContent(),
                nickname(user), avatar(user), answer.getCreatedAt());
    }

    private ProductQuestion requireQuestion(Long productId, Long questionId) {
        requireProduct(productId);
        ProductQuestion question = questionMapper.selectById(questionId);
        if (question == null || !productId.equals(question.getProductId())) {
            throw new BizException(ResultCode.NOT_FOUND, "提问不存在");
        }
        return question;
    }

    private void requireProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在");
        }
    }

    private Map<Long, User> loadUsers(List<Long> userIds) {
        List<Long> ids = userIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return userMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private String nickname(User user) {
        return user == null || !StringUtils.hasText(user.getNickname())
                ? "匿名用户" : user.getNickname();
    }

    private String avatar(User user) {
        return user == null || user.getAvatar() == null ? "" : user.getAvatar();
    }

    private int normalizePage(int page) {
        return Math.max(1, page);
    }

    private int normalizeSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }
}
