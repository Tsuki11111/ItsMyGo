package com.tsuk1.itsmygo.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 管理端写操作的审计元数据；未标注的写接口由切面按 URI 兜底。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminAudit {

    /** 稳定动作 code，如 ORDER_SHIP。 */
    String action();

    /** 中文动作名，写入 summary。 */
    String label();

    /** 目标类型，如 ORDER / PRODUCT / USER。 */
    String targetType();
}
