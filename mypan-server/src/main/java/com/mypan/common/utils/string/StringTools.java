package com.mypan.common.utils.string;


import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;


public final class StringTools {

    private static final String LITERAL_NULL = "null";
    private static final String LITERAL_UNDEFINED = "undefined";

    /** 共享 Random，避免每次 new */
    private static final SecureRandom RANDOM = new SecureRandom();

    private StringTools() {}

    // =================== MD5 ===================

    /** 对字符串做 MD5 摘要（非密码场景），入参为空返回 null */
    public static String md5Hex(String originString) {
        return StringUtils.hasText(originString) ? DigestUtils.md5Hex(originString) : null;
    }

    // =================== 文件名相关 ===================

    /** 获取文件后缀（包含点），没有则返回空串 */
    public static String getSuffix(String fileName) {
        if (fileName == null) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        if (index <= 0 || index == fileName.length() - 1) {
            // 形如 ".gitignore" 或 "a." 都视为无后缀
            return "";
        }
        return fileName.substring(index);
    }

    /** 获取去掉后缀的文件名 */
    public static String removeSuffix(String fileName) {
        if (fileName == null) {
            return null;
        }
        int index = fileName.lastIndexOf('.');
        if (index <= 0) {
            return fileName;
        }
        return fileName.substring(0, index);
    }

    /**
     * 根据已占用名称集合，生成一个不冲突的新名字
     * 规则：name.ext -> name(1).ext -> name(2).ext ...
     * NOTE: 每次得到新名字后，都会把新名字 add 进 Set 中，外层使用无需再手动加
     */
    public static String resolveConflict(String fileName, Set<String> occupiedNames) {
        if (!occupiedNames.contains(fileName)) {
            return fileName;
        }

        String base = removeSuffix(fileName);
        String suffix = getSuffix(fileName);

        int index = 1;
        String candidate;
        do {
            candidate = base + "(" + index + ")" + suffix;
            index++;
        } while (occupiedNames.contains(candidate));
        occupiedNames.add(candidate);
        return candidate;
    }

    // =================== 随机串 / 数字 ===================

    private static final String RANDOM_CHARS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    public static String getRandomString(int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            int index = RANDOM.nextInt(RANDOM_CHARS.length());
            builder.append(RANDOM_CHARS.charAt(index));
        }
        return builder.toString();
    }

    public static String getRandomNumber(int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }

    // =================== 路径校验 ===================

    public static boolean isPathSegmentOk(String s) {
        if (!StringUtils.hasText(s)) return false;

        String v = s.trim();

        // 常见前端空值
        if ("null".equalsIgnoreCase(v) || "undefined".equalsIgnoreCase(v)) return false;

        // 单段不允许分隔符
        if (v.indexOf('/') >= 0 || v.indexOf('\\') >= 0) return false;

        // 长度限制
        if (v.length() > 180) return false;

        // 禁止当前目录 / 上级目录这种特殊段
        if (".".equals(v) || "..".equals(v)) return false;

        // 白名单：字母数字 . _ -
        if (!v.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) return false;

        return true;
    }

    public static boolean isRelPathOk(String path) {
        if (!StringUtils.hasText(path)) return false;

        String v = path.trim();

        if ("null".equalsIgnoreCase(v) || "undefined".equalsIgnoreCase(v)) return false;

        // 相对路径可稍长一点
        if (v.length() > 300) return false;

        // 先统一分隔符
        v = v.replace('\\', '/');

        // 基础结构限制
        if (v.startsWith("/") || v.endsWith("/")) return false;
        if (v.contains("//")) return false;

        String[] parts = v.split("/");
        if (parts.length == 0) return false;

        // 先做 segment 白名单校验
        for (String seg : parts) {
            if (!isPathSegmentOk(seg)) return false;
        }

        // 再做规范化校验，防语义级绕过
        try {
            Path normalized = Paths.get(v).normalize();

            // 必须仍然是相对路径
            if (normalized.isAbsolute()) return false;

            // normalize 后不能跳到上级
            if (normalized.startsWith("..")) return false;

            // normalize 后不应为空
            String normalizedStr = normalized.toString().replace('\\', '/');
            if (!StringUtils.hasText(normalizedStr)) return false;

            // 可选：规范化后再做一次分段校验，确保结果仍符合白名单
            String[] normalizedParts = normalizedStr.split("/");
            for (String seg : normalizedParts) {
                if (!isPathSegmentOk(seg)) return false;
            }

            return true;
        } catch (InvalidPathException e) {
            return false;
        }
    }

    // =================== 下划线转驼峰 ===================

    public static String underlineToCamel(String param) {
        if (!StringUtils.hasText(param)) {
            return "";
        }
        StringBuilder sb = new StringBuilder(param.length());
        boolean nextUpper = false;
        for (int i = 0; i < param.length(); i++) {
            char c = param.charAt(i);
            if (c == '_') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    sb.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    public static final class FileNameValidator {

        private FileNameValidator() {}

        // Windows 禁止字符（也覆盖 Linux 的路径分隔风险）
        private static final Set<Character> ILLEGAL_CHARS = Set.of(
                '\\', '/', ':', '*', '?', '"', '<', '>', '|'
        );

        // Windows 保留设备名（不区分大小写），不允许作为“主文件名”（不含扩展名）
        private static final Set<String> RESERVED_NAMES = Set.of(
                "CON", "PRN", "AUX", "NUL",
                "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
                "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
        );

        // 绝大多数文件系统对单个文件名长度限制 255 bytes 左右，这里按字符做一个保守限制
        private static final int MAX_NAME_LENGTH = 255;

        /**
         * 校验文件/目录名（不包含路径）。非法直接抛异常。
         * @param name 文件名或目录名
         * @param allowDot 是否允许 "." 出现（文件扩展名需要允许）；目录一般也可以允许，但你如果不想要“.” 结尾等，可以靠规则拦
         */
        public static void validateSimpleName(String name, boolean allowDot) {
            if (name == null) {
                throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
            }
            String n = name.trim();
            if (n.isEmpty()) {
                throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
            }
            // 防止 "." / ".."
            if (n.equals(".") || n.equals("..")) {
                throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
            }
            if (n.length() > MAX_NAME_LENGTH) {
                throw new BusinessException("File name is too long.");
            }

            // Windows：末尾不能是空格或点
            char last = n.charAt(n.length() - 1);
            if (last == ' ' || last == '.') {
                throw new BusinessException("File name cannot end with a space or dot.");
            }

            // 控制字符 + 非法字符
            for (int i = 0; i < n.length(); i++) {
                char c = n.charAt(i);
                if (c <= 31) { // ASCII control chars
                    throw new BusinessException("File name contains control characters.");
                }
                if (!allowDot && c == '.') {
                    throw new BusinessException("Folder name cannot contain dots.");
                }
                if (ILLEGAL_CHARS.contains(c)) {
                    throw new BusinessException("File name contains illegal character: " + c);
                }
            }

            // Windows 保留设备名：只检查“主名”（去掉最后一个扩展名）
            String base = n;
            int dot = n.lastIndexOf('.');
            if (dot > 0) {
                base = n.substring(0, dot);
            }
            String upperBase = base.toUpperCase(Locale.ROOT);
            if (RESERVED_NAMES.contains(upperBase)) {
                throw new BusinessException("File name is reserved.");
            }
        }
    }

    public static List<String> parseDelimitedDistinctList(String input, String delimiter) {
        if (!StringUtils.hasText(input)) {
            return List.of();
        }
        return Arrays.stream(input.split(delimiter))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }
}