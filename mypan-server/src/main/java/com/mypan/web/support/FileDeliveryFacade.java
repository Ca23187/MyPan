package com.mypan.web.support;

import com.mypan.common.constants.Constants;
import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import com.mypan.common.utils.file.AudioTools;
import com.mypan.common.utils.servlet.ServletNetUtils;
import com.mypan.infra.redis.RedisComponent;
import com.mypan.service.dto.download.DownloadPlan;
import com.mypan.service.dto.download.ZipEntryResource;
import com.mypan.service.dto.responseWrite.FileReadResourceDto;
import com.mypan.service.file.access.FileAccessService;
import com.mypan.service.file.download.FileDownloadService;
import com.mypan.service.file.storage.BasicStorageService;
import com.mypan.web.dto.response.AudioMetaVo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.*;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class FileDeliveryFacade {

    private final FileAccessService fileAccessService;
    private final BasicStorageService storageService;
    private final FileDownloadService fileDownloadService;
    private final RedisComponent redisComponent;

    public void writeResource(HttpServletRequest request, HttpServletResponse response, FileReadResourceDto res) {
        String contentType = StringUtils.hasText(res.getContentType())
                ? res.getContentType()
                : "application/octet-stream";
        if (res.supportsRange()) {
            response.setHeader("Accept-Ranges", "bytes");
        }
        response.setContentType(contentType);

        // HEAD：只回 header，不开流
        if ("HEAD".equalsIgnoreCase(request.getMethod())) {
            response.setContentLengthLong(res.getContentLength());
            return;
        }

        // Range
        String range = request.getHeader("Range");
        if (StringUtils.hasText(range) && res.supportsRange()) {
            if (contentType.startsWith("audio/")) {
                response.setHeader("X-Accel-Buffering", "no");
            }
            Optional<HttpRange> parsed = HttpRange.parse(range, res.getContentLength());
            // Range 头存在但不可解析/不支持（如多段）/越界 => 416 更标准
            if (parsed.isEmpty()) {
                // 如果 response 可能已经写入了一部分，先 reset（可选但更稳）
                try {
                    response.resetBuffer();
                } catch (Exception ignore) {}

                response.setStatus(416); // Range Not Satisfiable
                response.setHeader("Content-Range", "bytes */" + res.getContentLength());
                // Content-Length 不必设置，返回空 body 即可
                return;
            }
            HttpRange r = parsed.get();

            response.setStatus(206);
            response.setHeader("Content-Range", "bytes " + r.start + "-" + r.end + "/" + res.getContentLength());
            response.setContentLengthLong(r.length);

            try (InputStream in = res.getOpenRange().apply(r.start, r.length)) {
                OutputStream out = response.getOutputStream();
                byte[] buf = new byte[64 * 1024];
                long sinceFlush = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    sinceFlush += n;

                    // 让写入尽快落到网络层，尽快发现客户端断开
                    if (sinceFlush >= 256L * 1024) {
                        out.flush();
                        sinceFlush = 0;
                    }
                }
                out.flush();
                return;
            } catch (IOException | UncheckedIOException e) {
                if (ServletNetUtils.isClientAbort(e)) return;
                throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
            }
        }

        // 普通整文件
        response.setContentLengthLong(res.getContentLength());
        try (InputStream in = res.getOpenStream().get()) {
            OutputStream out = response.getOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            out.flush();
        } catch (IOException | UncheckedIOException e) {
            if (ServletNetUtils.isClientAbort(e)) return;
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        }
    }

    public void download(HttpServletRequest request, HttpServletResponse response, String code) {
        if ("HEAD".equalsIgnoreCase(request.getMethod())) {
            return;
        }

        // 防止有人拿同一个 code 在 code 有效期内刷爆
        if (!redisComponent.tryAcquireDownloadRate(code, request.getRemoteAddr())) {
            throw new BusinessException(429, "Too many download requests, please retry later.");
        }

        DownloadPlan plan = fileDownloadService.resolveDownloadPlan(code);

        String singlePermitId = null;
        try {
            if (plan.getType() == DownloadPlan.Type.SINGLE) {
                // 单文件：不互斥（支持206），做 inflight 并发上限
                singlePermitId = redisComponent.tryAcquireSingleDownloadPermit(code);
                if (!StringUtils.hasText(singlePermitId)) {
                    throw new BusinessException("Too many concurrent downloads");
                }

                FileReadResourceDto res = fileAccessService.openForDownload(plan.getObjectKey());
                setAttachmentHeader(response, plan.getFileName());
                writeResource(request, response, res);
                return;
            }

            // ZIP：code 互斥锁防并发
            RLock zipLock = redisComponent.getZipDownloadLock(code);
            boolean zipLocked = false;
            try {
                zipLocked = zipLock.tryLock(0, TimeUnit.SECONDS);
                if (!zipLocked) {
                    throw new BusinessException("A ZIP download is already in progress for this link. Please retry later.");
                }

                response.setHeader("X-Accel-Buffering", "no");
                response.setContentType("application/zip");
                setAttachmentHeader(response, plan.getZipName());

                try (ZipOutputStream zos = new ZipOutputStream(
                        new BufferedOutputStream(response.getOutputStream(), 64 * 1024))) {
                    byte[] buf = new byte[8192];

                    for (ZipEntryResource e : plan.getEntries()) {
                        String entryName = sanitizeZipEntryName(e.getEntryName());

                        zos.putNextEntry(new ZipEntry(entryName));
                        if (e.isDir()) {
                            zos.closeEntry();
                            continue;
                        }

                        try (InputStream in = storageService.get(e.getObjectKey())) {
                            int len;
                            while ((len = in.read(buf)) != -1) {
                                zos.write(buf, 0, len);
                            }
                        }

                        zos.closeEntry();
                        zos.flush();
                    }
                    zos.finish();
                } catch (IOException e) {
                    if (ServletNetUtils.isClientAbort(e)) {
                        return;
                    }
                    throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException("Obtaining ZIP download lock was interrupted");
            } finally {
                if (zipLocked && zipLock.isHeldByCurrentThread()) {
                    zipLock.unlock();
                }
            }
        } catch (IOException e) {
            if (ServletNetUtils.isClientAbort(e)) {
                return;
            }
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        } finally {
            if (StringUtils.hasText(singlePermitId)) {
                redisComponent.releaseSingleDownloadPermit(code, singlePermitId);
            }
        }
    }

    /**
     * 防 Zip Slip：禁止绝对路径/上跳路径
     */
    private String sanitizeZipEntryName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
        }
        String n = name.replace('\\', '/');
        if (n.startsWith("/") || n.contains("../") || n.contains("..")) {
            throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
        }
        return n;
    }

    /** Range 结构 */
    static class HttpRange {
        final long start;
        final long end;
        final long length;

        HttpRange(long start, long end) {
            this.start = start;
            this.end = end;
            this.length = end - start + 1;
        }

        static Optional<HttpRange> parse(String rangeHeader, long totalLength) {
            // only support single range: bytes=Start-End | bytes=Start- | bytes=-Suffix
            try {
                String v = rangeHeader.trim();
                if (!v.startsWith("bytes=")) return Optional.empty();
                v = v.substring("bytes=".length()).trim();
                if (v.contains(",")) return Optional.empty(); // 不支持多段

                String[] parts = v.split("-", 2);
                String a = parts[0].trim();
                String b = parts.length > 1 ? parts[1].trim() : "";

                long start, end;

                if (a.isEmpty()) {
                    // bytes=-Suffix
                    long suffix = Long.parseLong(b);
                    if (suffix <= 0) return Optional.empty();
                    if (suffix > totalLength) suffix = totalLength;
                    start = totalLength - suffix;
                    end = totalLength - 1;
                } else {
                    start = Long.parseLong(a);
                    if (start < 0 || start >= totalLength) return Optional.empty();

                    if (b.isEmpty()) {
                        // bytes=Start-
                        end = totalLength - 1;
                    } else {
                        end = Long.parseLong(b);
                        if (end < start) return Optional.empty();
                        if (end >= totalLength) end = totalLength - 1;
                    }
                }

                return Optional.of(new HttpRange(start, end));
            } catch (Exception ignore) {
                return Optional.empty();
            }
        }
    }

    public AudioMetaVo getAudioMeta(String fileId, String userId) {
        FileReadResourceDto res = fileAccessService.openForRead(fileId, userId);
        String contentType = StringUtils.hasText(res.getContentType())
                ? res.getContentType()
                : "application/octet-stream";

        // 只读头部一段，减少 IO 压力
        long max = Constants.MB;
        long readLen = res.getContentLength() > 0 ? Math.min(max, res.getContentLength()) : max;

        try (InputStream in = res.supportsRange()
                ? res.getOpenRange().apply(0L, readLen)
                : res.getOpenStream().get()) {
            return AudioTools.extractMeta(in, res.getContentLength(), contentType);
        } catch (Exception e) {
            return new AudioMetaVo();
        }
    }

    private void setAttachmentHeader(HttpServletResponse response,
                                           String filename) throws UnsupportedEncodingException {
        String encoded = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
    }
}