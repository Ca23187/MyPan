package com.mypan.common.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class ResponseVo<T> {
    private Integer code;
    private String info;
    private T data;

    public static ResponseVo<Void> ok() {
        return ok(null);
    }

    public static <T> ResponseVo<T> ok(T data) {
        return build(ResponseCodeEnum.OK.getCode(), ResponseCodeEnum.OK.getMsg(), data);
    }

    public static ResponseVo<Void> error(ResponseCodeEnum codeEnum) {
        return error(codeEnum.getCode(), codeEnum.getMsg());
    }

    public static ResponseVo<Void> error(Integer code, String message) {
        return build(code, message, null);
    }

    private static <T> ResponseVo<T> build(Integer code, String info, T data) {
        ResponseVo<T> responseVo = new ResponseVo<>();
        responseVo.setCode(code);
        responseVo.setInfo(info);
        responseVo.setData(data);
        return responseVo;
    }
}