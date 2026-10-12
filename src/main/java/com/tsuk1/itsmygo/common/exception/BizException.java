package com.tsuk1.itsmygo.common.exception;

import com.tsuk1.itsmygo.common.ResultCode;
import lombok.Getter;

/**
 * 业务异常：可预期的失败（参数不对、库存不足、状态非法等）。
 * 由 {@link GlobalExceptionHandler} 转成统一响应，不打印堆栈。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }
}
