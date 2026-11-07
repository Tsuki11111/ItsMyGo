package com.tsuk1.itsmygo.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 断连回归：客户端断开不是服务端故障，全局处理器不应把它当「未预期异常」记录/包装。
 */
class GlobalExceptionHandlerTest {

    @Test
    void asyncDisconnectIsIgnoredInsteadOfLoggedAsUnexpected() throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        AsyncRequestNotUsableException e = new AsyncRequestNotUsableException(
                "Servlet container error notification for disconnected client");
        assertDoesNotThrow(() -> handler.handleAsyncRequestNotUsable(e));

        // 必须真的挂在全局处理器上（只写方法不注册 = 静默失效）
        Method method = GlobalExceptionHandler.class.getMethod(
                "handleAsyncRequestNotUsable", AsyncRequestNotUsableException.class);
        assertNotNull(method.getAnnotation(ExceptionHandler.class),
                "断连处理方法必须标注 @ExceptionHandler");
    }
}
