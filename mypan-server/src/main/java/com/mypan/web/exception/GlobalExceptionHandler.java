package com.mypan.web.exception;

import com.mypan.common.annotation.StreamResponse;
import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import com.mypan.common.response.ResponseVo;
import com.mypan.common.utils.servlet.ServletNetUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.io.IOException;
import java.net.SocketException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({ClientAbortException.class, AsyncRequestNotUsableException.class})
    public void handleClientAbort(Exception e, HttpServletRequest request, HttpServletResponse response) {
        log.info("客户端中断连接(忽略)：URL={}, msg={}", request.getRequestURL(), rootMsg(e));
        response.setStatus(499); // 或者 204/200
    }

    @ExceptionHandler({IOException.class, SocketException.class})
    public ResponseVo<?> handleIo(IOException e, HttpServletRequest request, HttpServletResponse response) {
        if (ServletNetUtils.isClientAbort(e)) {
            log.info("客户端中断连接(忽略)：URL={}, msg={}", request.getRequestURL(), rootMsg(e));
            response.setStatus(499); // 或 204
            return ResponseVo.error(499, "CLIENT_ABORT");
        }
        // 真正的 IO 异常：按系统异常处理
        log.error("IO异常，URL={}", request.getRequestURL(), e);
        return ResponseVo.error(ResponseCodeEnum.INTERNAL_ERROR);
    }

    /** 参数校验异常（@RequestBody + @Valid） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseVo<?> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                      HttpServletRequest request) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("参数校验失败");

        log.warn("请求体参数校验失败，URL={}, msg={}", request.getRequestURL(), msg);
        return ResponseVo.error(ResponseCodeEnum.BAD_REQUEST.getCode(), msg);
    }

    /** 参数校验异常（controller方法参数校验）*/
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseVo<?> handleHandlerMethodValidation(
            HandlerMethodValidationException e,
            HttpServletRequest request
    ) {
        String msg = e.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream())
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("参数校验失败");

        log.warn("方法参数校验错误，URL={}, 错误={}", request.getRequestURL(), msg);
        return ResponseVo.error(ResponseCodeEnum.BAD_REQUEST.getCode(), msg);
    }

    /** 参数绑定异常 */
    @ExceptionHandler({
            BindException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    public ResponseVo<?> handleBindException(Exception e, HttpServletRequest request) {
        log.warn("参数绑定/缺失异常，URL={}, error={}", request.getRequestURL(), e.getMessage());
        return ResponseVo.error(ResponseCodeEnum.BAD_REQUEST.getCode(), "请求参数错误");
    }

    /** JSON 格式错、body 为空、字段类型错误 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseVo<?> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
                                                      HttpServletRequest request) {
        log.warn("请求体解析失败，URL={}, error={}", request.getRequestURL(), rootMsg(e));
        return ResponseVo.error(ResponseCodeEnum.BAD_REQUEST.getCode(), "请求体格式错误");
    }

    /** 业务异常 */
    @ExceptionHandler(BusinessException.class)
    public ResponseVo<?> handleBusinessException(BusinessException e,
                                                 HttpServletRequest request,
                                                 HttpServletResponse response) {
        log.warn("业务异常，URL={}, 错误={}", request.getRequestURL(), e.getMessage());

        int code = (e.getCode() == null ? ResponseCodeEnum.BAD_REQUEST.getCode() : e.getCode());

        if (isStreamHandler(request)) {
            // 登录失效：401 + header
            if (code == ResponseCodeEnum.NOT_LOGGED_IN.getCode()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setHeader("X-Auth-Expired", "1");
                return null;
            }
            // 无权限：403
            if (code == ResponseCodeEnum.NO_PERMISSION.getCode()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return null;
            }

            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        // 非资源流：保持原 JSON 返回
        return ResponseVo.error(code, e.getMessage());
    }

    private boolean isStreamHandler(HttpServletRequest request) {
        Object handler = request.getAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingHandler");
        if (!(handler instanceof HandlerMethod hm)) return false;
        return hm.hasMethodAnnotation(StreamResponse.class);
    }

    /** 主键冲突 */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseVo<?> handleDuplicateKeyException(DuplicateKeyException e, HttpServletRequest request) {
        log.error("数据库主键冲突，URL={}, 错误={}", request.getRequestURL(), e.getMessage());
        return ResponseVo.error(ResponseCodeEnum.ALREADY_EXISTS);
    }

    /** 404 */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseVo<?> handleNotFound(NoHandlerFoundException e, HttpServletRequest request) {
        log.warn("地址不存在，URL={}, 错误={}", request.getRequestURL(), e.getMessage());
        return ResponseVo.error(ResponseCodeEnum.NOT_FOUND);
    }

    /** 其他所有异常 */
    @ExceptionHandler(Exception.class)
    public ResponseVo<?> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常，URL={}", request.getRequestURL(), e);
        return ResponseVo.error(ResponseCodeEnum.INTERNAL_ERROR);
    }

    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    public ResponseVo<?> handleMultipart(MultipartException e, HttpServletRequest request, HttpServletResponse response) {
        // Tomcat 读 multipart 时被客户端中断，常见于 AbortController / 断网 / 刷新页面
        if (ServletNetUtils.isClientAbort(e)) {
            log.info("客户端中断上传(忽略)：URL={}, msg={}", request.getRequestURI(), rootMsg(e));
            // 返回一个“非错误”的响应也行；但通常前端 abort 了也收不到
            response.setStatus(499);
            return null;
        }
        if (e instanceof MaxUploadSizeExceededException) {
            log.warn("文件过大：URL={}, msg={}", request.getRequestURI(), rootMsg(e));
            return ResponseVo.error(ResponseCodeEnum.BAD_REQUEST.getCode(), "文件过大");
        }
        log.error("Multipart 解析失败：URL={}, msg={}", request.getRequestURI(), rootMsg(e), e);
        return ResponseVo.error(ResponseCodeEnum.BAD_REQUEST.getCode(), "上传失败");
    }

    private String rootMsg(Throwable e) {
        Throwable t = e;
        Throwable last = e;
        while (t != null) {
            last = t;
            t = t.getCause();
        }
        return String.valueOf(last.getMessage());
    }
}
