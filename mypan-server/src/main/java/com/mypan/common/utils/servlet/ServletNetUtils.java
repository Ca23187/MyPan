package com.mypan.common.utils.servlet;

import io.minio.errors.ErrorResponseException;

public final class ServletNetUtils {

    private ServletNetUtils() {}

    public static boolean isClientAbort(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String className = t.getClass().getName();

            // Tomcat / Undertow / Netty 等
            if (className.contains("ClientAbortException")) {
                return true;
            }

            String msg = t.getMessage();
            if (msg == null) continue;

            String m = msg.toLowerCase();

            // 各平台 / 代理常见断连信息
            if (m.contains("broken pipe")
                    || m.contains("connection reset")
                    || m.contains("connection aborted")
                    || m.contains("reset by peer")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isRecoverableMinioError(Throwable e) {
        if (e == null) return false;

        // 1) 用户暂停/取消（连接断开）
        if (ServletNetUtils.isClientAbort(e)) return true;

        for (Throwable t = e; t != null; t = t.getCause()) {

            // 2) SDK/网络层：一般都可重试
            if (t instanceof java.io.InterruptedIOException) return true;
            if (t instanceof java.net.ConnectException) return true;
            if (t instanceof java.net.SocketException) return true;
            if (t instanceof java.io.EOFException) return true;

            // 3) MinIO 服务端明确错误：看 S3 error code
            if (t instanceof ErrorResponseException ere) {
                String code = ere.errorResponse() != null ? ere.errorResponse().code() : null;

                // 明确不可恢复：uploadId 不存在 / 权限 / 参数问题
                if ("NoSuchUpload".equals(code)     // MPU 已失效/被清理
                        || "AccessDenied".equals(code)
                        || "InvalidAccessKeyId".equals(code)
                        || "SignatureDoesNotMatch".equals(code)
                        || "InvalidArgument".equals(code)
                        || "InvalidPart".equals(code)
                        || "InvalidPartOrder".equals(code)
                ) {
                    return false;
                }

                // 多数 5xx / 临时性错误：可恢复
                // MinIO 有时 code 会是 "InternalError" / "ServiceUnavailable" 等
                if ("InternalError".equals(code)
                        || "ServiceUnavailable".equals(code)
                        || "SlowDown".equals(code)
                ) {
                    return true;
                }

                // 其他未知 code：保守一点——默认不可恢复
                // （避免一直让前端重试但永远不可能成功）
                continue;
            }

            // 4) message 兜底（代理/网关/HTTP client 的常见文本）
            String msg = t.getMessage();
            if (msg != null) {
                String m = msg.toLowerCase();
                if (m.contains("timeout")
                        || m.contains("timed out")
                        || m.contains("connection reset")
                        || m.contains("reset by peer")
                        || m.contains("broken pipe")
                        || m.contains("unexpected end of stream")
                        || m.contains("stream closed")
                        || m.contains("service unavailable")
                        || m.contains("gateway timeout")
                        || m.contains("bad gateway")) {
                    return true;
                }
            }
        }
        return false;
    }
}
