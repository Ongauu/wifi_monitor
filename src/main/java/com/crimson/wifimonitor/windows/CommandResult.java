package com.crimson.wifimonitor.windows;

public record CommandResult(int exitCode, String output, boolean timedOut, String errorMessage) {

    public boolean succeeded() {
        return !timedOut && errorMessage == null && exitCode == 0;
    }
}
