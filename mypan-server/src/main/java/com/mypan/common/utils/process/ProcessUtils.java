package com.mypan.common.utils.process;

import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public final class ProcessUtils {
    
    // 默认超时时间（分钟）
    private static final long DEFAULT_TIMEOUT_MINUTES = 30L;

    private ProcessUtils() {}

    // ================== 对外方法 ==================

    /** 执行命令（参数列表方式，推荐） */
    public static int exec(List<String> cmd, Path workDir, boolean printOutput) {
        return exec(cmd, workDir, DEFAULT_TIMEOUT_MINUTES, printOutput);
    }

    /** 执行命令（参数列表方式，带超时） */
    public static int exec(List<String> cmd, Path workDir, long timeoutMinutes, boolean printOutput) {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (workDir != null) {
            pb.directory(workDir.toFile());
        }
        return doExec(pb, timeoutMinutes, printOutput, cmd.toString());
    }

    /** 执行命令（字符串方式，通过 shell，适用于包含管道/重定向的复杂命令） */
    public static int exec(String cmd, Path workDir, boolean printOutput) {
        return exec(cmd, workDir, DEFAULT_TIMEOUT_MINUTES, printOutput);
    }

    public static int exec(String cmd, Path workDir, long timeoutMinutes, boolean printOutput) {
        ProcessBuilder pb = buildShellProcess(cmd);
        if (workDir != null) {
            pb.directory(workDir.toFile());
        }
        return doExec(pb, timeoutMinutes, printOutput, cmd);
    }

    // ================== 内部公共实现 ==================

    /** 根据当前 OS 选择合适的 shell 命令封装 String cmd */
    private static ProcessBuilder buildShellProcess(String cmd) {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            // Windows: 使用 cmd.exe
            return new ProcessBuilder("cmd.exe", "/c", cmd);
        } else {
            // Linux / macOS / Unix
            return new ProcessBuilder("/bin/sh", "-c", cmd);
        }
    }

    /** 真正执行进程 + 超时控制 + 吃输出 */
    private static int doExec(ProcessBuilder pb,
                              long timeoutMinutes,
                              boolean printOutput,
                              String cmdForLog) {

        try {
            Process process = pb.start();

            int keepLines = printOutput ? 1000 : 200;
            StreamGobbler stdoutGobbler = new StreamGobbler(process.getInputStream(), keepLines);
            StreamGobbler stderrGobbler = new StreamGobbler(process.getErrorStream(), keepLines);

            stdoutGobbler.start();
            stderrGobbler.start();

            boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                // 再等最多 10 秒，确保退出
                process.waitFor(10, TimeUnit.SECONDS);

                // 关键：关闭流，促使 gobbler 尽快结束，释放句柄（Windows 特别重要）
                try { process.getInputStream().close(); } catch (Exception ignored) {}
                try { process.getErrorStream().close(); } catch (Exception ignored) {}
                try { process.getOutputStream().close(); } catch (Exception ignored) {}

                log.error("命令执行超时: {}", cmdForLog);
                throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
            }


            stdoutGobbler.join(TimeUnit.SECONDS.toMillis(5));
            stderrGobbler.join(TimeUnit.SECONDS.toMillis(5));

            int exitCode = process.exitValue();
            String stdout = stdoutGobbler.getContent();
            String stderr = stderrGobbler.getContent();

            if (printOutput) {
                log.info("命令执行完毕: {}\n退出码: {}\nstdout:\n{}\nstderr:\n{}",
                        cmdForLog, exitCode, stdout, stderr);
            } else {
                if (exitCode != 0) {
                    log.warn("命令执行失败: {}, exitCode={}, stderr(last):\n{}",
                            cmdForLog, exitCode, stderr);
                } else {
                    log.info("命令执行完毕: {}, 退出码: {}", cmdForLog, exitCode);
                }
            }

            // 你可以根据业务情况把 stdout/derr 也一并 return/封装，这里简单只返回 exitCode
            return exitCode;
        } catch (IOException e) {
            log.error("执行命令 IO 异常, cmd={}", cmdForLog, e);
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("执行命令被中断, cmd={}", cmdForLog, e);
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        }
    }

    /** 读取子进程输出流的线程 */
    private static class StreamGobbler extends Thread {
        private final InputStream inputStream;
        private final int keepLines;
        private final ArrayDeque<String> lastLines = new ArrayDeque<>();

        StreamGobbler(InputStream inputStream, int keepLines) {
            this.inputStream = inputStream;
            this.keepLines = Math.max(0, keepLines);
            setDaemon(true);
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (keepLines == 0) continue; // 丢弃所有内容
                    if (keepLines == Integer.MAX_VALUE) {
                        // 全量保留：仍然可能很大，但只在 printOutput=true 时用
                        lastLines.addLast(line);
                    } else {
                        // 只保留最后 keepLines 行
                        if (lastLines.size() >= keepLines) {
                            lastLines.removeFirst();
                        }
                        lastLines.addLast(line);
                    }
                }
            } catch (IOException e) {
                log.warn("读取子进程输出时出错: {}", e.getMessage());
            }
        }

        String getContent() {
            if (lastLines.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (String s : lastLines) sb.append(s).append('\n');
            return sb.toString();
        }
    }

    public static int execNoThrow(List<String> cmd, Path workDir, long timeoutMinutes, boolean printOutput) {
        try {
            return exec(cmd, workDir, timeoutMinutes, printOutput);
        } catch (Exception e) {
            return -1;
        }
    }
}

