package com.staging.sg.deployment.model;

public enum ShellType {
    GIT_BASH(TargetOs.WINDOWS),
    POWERSHELL(TargetOs.WINDOWS),
    CMD_WINDOWS(TargetOs.WINDOWS),
    BASH_LINUX(TargetOs.LINUX);

    private final TargetOs targetOs;

    ShellType(TargetOs targetOs) {
        this.targetOs = targetOs;
    }

    public boolean supports(TargetOs os) {
        return targetOs == os;
    }
}
