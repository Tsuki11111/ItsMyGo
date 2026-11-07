package com.tsuk1.itsmygo.service;

/** 智能推广：给单个用户推荐一条商品，或每日批量执行。 */
public interface PromotionService {

    /**
     * 给一个用户推荐并发送一条推广消息。
     * 未开推广、今天已推、没有合适商品都返回 false；发送成功返回 true。
     * 用户/店铺/商品异常只记日志并返回 false，不允许拖垮批量任务。
     */
    boolean notifyForUser(long userId);

    /** 每日批量：取开启推广的启用用户逐个发送；返回成功发送人数。 */
    int runDaily();
}
