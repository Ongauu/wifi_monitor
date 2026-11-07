package com.crimson.wifimonitor.windows;

import com.crimson.wifimonitor.config.WifiMonitorProperties;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WindowsCommandExecutor {

    private final WifiMonitorProperties properties;

    public CommandResult execute(List<String> command) {
        return execute(command, Duration.ofMillis(properties.getCommandTimeoutMs()));
    }

    public CommandResult execute(List<String> command, Duration timeout) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();
            Process running = process;
            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readOutput(running));
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Command timed out after {} ms: {}", timeout.toMillis(), command);
                String partial = outputFuture.completeOnTimeout("", 500, TimeUnit.MILLISECONDS).join();
                return new CommandResult(-1, truncate(partial), true, "Command timed out");
            }
            String output = outputFuture.get(2, TimeUnit.SECONDS);
            return new CommandResult(process.exitValue(), truncate(output), false, null);
        } catch (Exception ex) {
            log.warn("Failed to execute command {}: {}", command, ex.getMessage());
            if (process != null) {
                process.destroyForcibly();
            }
            return new CommandResult(-1, "", false, ex.getMessage());
        }
    }

    private String readOutput(Process process) {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append(System.lineSeparator());
                if (builder.length() > properties.getMaxCommandOutputChars() * 2) {
                    break;
                }
            }
        } catch (Exception ex) {
            log.debug("Unable to read command output: {}", ex.getMessage());
        }
        return builder.toString();
    }

    private String truncate(String output) {
        if (output == null) {
            return "";
        }
        int max = properties.getMaxCommandOutputChars();
        if (output.length() <= max) {
            return output.trim();
        }
        return output.substring(0, max).trim() + "\n...[truncated]";
    }
}
