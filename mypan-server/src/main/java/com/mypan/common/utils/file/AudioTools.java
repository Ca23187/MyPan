package com.mypan.common.utils.file;

import com.mypan.web.dto.response.AudioMetaVo;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public final class AudioTools {
    public static AudioMetaVo extractMeta(InputStream in, long contentLength, String contentType) {
        File tmp = null;
        try {
            String suffix = guessSuffix(contentType); // 关键
            tmp = File.createTempFile("audio-meta-", suffix);
            tmp.deleteOnExit();
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(tmp))) {
                in.transferTo(out);
            }

            AudioFile af = AudioFileIO.read(tmp);
            Tag tag = af.getTag();
            AudioHeader header = af.getAudioHeader();

            AudioMetaVo dto = new AudioMetaVo();
            if (tag != null) {
                dto.setTitle(tag.getFirst(FieldKey.TITLE));
                dto.setArtist(tag.getFirst(FieldKey.ARTIST));
                dto.setAlbum(tag.getFirst(FieldKey.ALBUM));
                dto.setYear(tag.getFirst(FieldKey.YEAR));
                dto.setGenre(tag.getFirst(FieldKey.GENRE));
                dto.setTrack(tag.getFirst(FieldKey.TRACK));
            }

            if (header != null) {
                dto.setDurationSec(header.getTrackLength());
                dto.setBitDepth(header.getBitsPerSample());
                dto.setSampleRateHz(header.getSampleRateAsNumber());
                dto.setIsLossless(header.isLossless());
                dto.setBitrateKbps(header.getBitRateAsNumber());
                // 兜底策略：无损 或 库给不出有效值 -> 自己算平均码率
                int duration = header.getTrackLength();
                if (header.isLossless() || header.getSampleRateAsNumber() <= 0) {
                    if (duration > 0) {
                        dto.setBitrateKbps(contentLength * 8 / duration / 1000L); // 平均码率 Kbps
                    }
                } else {
                    dto.setBitrateKbps(header.getBitRateAsNumber());
                }
            }
            return dto;
        } catch (Exception e) {
            return new AudioMetaVo();
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp.toPath());
                } catch (Exception ignore) {
                }
            }
        }
    }
    private static String guessSuffix(String contentType) {
        if (contentType == null) return ".mp3"; // 默认兜底
        String ct = contentType.toLowerCase(Locale.ROOT);

        // 常见音频
        if (ct.contains("mpeg") || ct.contains("mp3")) return ".mp3";
        if (ct.contains("mp4") || ct.contains("m4a") || ct.contains("aac")) return ".m4a";
        if (ct.contains("flac")) return ".flac";
        if (ct.contains("wav")) return ".wav";
        if (ct.contains("ogg")) return ".ogg";
        if (ct.contains("x-ms-wma") || ct.contains("wma")) return ".wma";

        // 实在不认识：用 .mp3 兜底（jaudiotagger 至少会尝试）
        return ".mp3";
    }

    public static boolean extractCover(Path audioFile, Path targetFile) {
        Path tmp = FileTools.getTmpPath(targetFile);
        try {
            AudioFile af = AudioFileIO.read(audioFile.toFile());
            Tag tag = af.getTag();
            if (tag == null) return false;

            Artwork artwork = tag.getFirstArtwork();
            if (artwork == null) return false;

            BufferedImage img = (BufferedImage) artwork.getImage();
            if (img == null) return false;

            try {  // 写成真正的 PNG
                ImageIO.write(img, "png", tmp.toFile());
            } catch (IOException e) {
                return false;
            }
            try {
                Files.move(tmp, targetFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, targetFile,
                        StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
        }
    }
}
