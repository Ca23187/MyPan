package com.mypan.common.utils.file;

import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@Slf4j
public class AvatarTools {

    public static byte[] normalizeAvatarToPng(MultipartFile avatar,
                                              int outSize,
                                              int maxSide,
                                              long maxPixels,
                                              long maxUploadBytes) {
        try {
            if (avatar == null || avatar.isEmpty())
                throw new BusinessException("头像文件不能为空");

            if (maxUploadBytes > 0 && avatar.getSize() > maxUploadBytes)
                throw new BusinessException("头像文件不能超过 " + (maxUploadBytes / 1024 / 1024) + "MB");

            BufferedImage src;
            try (InputStream in = avatar.getInputStream()) {
                src = ImageIO.read(in);
            }
            if (src == null)
                throw new BusinessException("请上传有效的图片文件");

            int w = src.getWidth();
            int h = src.getHeight();

            if (w <= 0 || h <= 0)
                throw new BusinessException("图片尺寸不合法");

            if (maxSide > 0 && (w > maxSide || h > maxSide))
                throw new BusinessException("图片尺寸过大，请上传分辨率较小的头像");

            if (maxPixels > 0 && (long) w * (long) h > maxPixels)
                throw new BusinessException("图片分辨率过高，请压缩后再上传");

            BufferedImage square = centerCropSquare(src);
            BufferedImage scaled = scaleTo(square, outSize, outSize);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(scaled, "png", out);
            return out.toByteArray();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("normalizeAvatarToPng failed", e);
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        }
    }

    private static BufferedImage centerCropSquare(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int side = Math.min(w, h);
        int x = (w - side) / 2;
        int y = (h - side) / 2;

        BufferedImage cropped = src.getSubimage(x, y, side, side);

        // getSubimage 是视图，拷贝一份更稳
        BufferedImage copy = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(cropped, 0, 0, null);
        g.dispose();
        return copy;
    }

    private static BufferedImage scaleTo(BufferedImage src, int targetW, int targetH) {
        BufferedImage dst = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = dst.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src, 0, 0, targetW, targetH, null);
        g2.dispose();
        return dst;
    }
}
