package com.mypan.common.exception;

import com.mypan.common.response.ResponseCodeEnum;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private ResponseCodeEnum codeEnum;
    private Integer code;

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(ResponseCodeEnum codeEnum) {
        super(codeEnum.getMsg());
        this.codeEnum = codeEnum;
        this.code = codeEnum.getCode();
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.codeEnum = null;
        this.code = code;
    }

    // 带 cause（给 MinIO/AWS SDK 的异常保留原因链）
    public BusinessException(ResponseCodeEnum codeEnum, Throwable cause) {
        // disableSuppression=false, writableStackTrace=false => 不生成堆栈，但保留 cause 链
        super(codeEnum.getMsg(), cause, false, false);
        this.codeEnum = codeEnum;
        this.code = codeEnum.getCode();
    }

    // 可选：也给 (code,message,cause) 版本，方便扩展
    public BusinessException(Integer code, String message, Throwable cause) {
        super(message, cause, false, false);
        this.codeEnum = null;
        this.code = code;
    }

    /**
     * 重写fillInStackTrace 业务异常不需要堆栈信息，提高效率.
     */
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

}