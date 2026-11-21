# Built for the host architecture. Google publishes the Desktop Head Unit and the
# native build-tools binaries for linux/amd64 only. On arm64 those four binaries
# are replaced with pinned AOSP builds and the DHU package is skipped.
FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

ENV ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_HOME=/opt/android-sdk \
    JAVA_HOME=/usr/lib/jvm/java-21 \
    PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:/opt/android-sdk/extras/google/auto:${PATH}

ARG TARGETARCH
ARG CMDTOOLS_REV=15859902
# Commit451/android-arm-build-tools release platform-tools-36.0.0 (AOSP android-16.0.0_r1).
ARG ARM64_BUILD_TOOLS_URL=https://github.com/Commit451/android-arm-build-tools/releases/download/platform-tools-36.0.0

RUN case "$TARGETARCH" in amd64|arm64) ;; *) echo "unsupported TARGETARCH=$TARGETARCH" >&2; exit 1 ;; esac \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
        openjdk-21-jdk-headless \
        unzip \
    && if [ "$TARGETARCH" = "amd64" ]; then \
        apt-get install -y --no-install-recommends \
            libc++1 \
            libc++abi1 \
            libusb-1.0-0 \
            libgl1 \
            libx11-6 \
            libxi6 \
            libxext6 \
            libxrender1; \
    else \
        apt-get install -y --no-install-recommends adb; \
    fi \
    && rm -rf /var/lib/apt/lists/* \
    && ln -sfn "/usr/lib/jvm/java-21-openjdk-$(dpkg --print-architecture)" /usr/lib/jvm/java-21

RUN curl -fsSL -o /tmp/cmdline-tools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-${CMDTOOLS_REV}_latest.zip" \
    && mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools \
    && rm /tmp/cmdline-tools.zip \
    && mv /tmp/cmdline-tools/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest"

RUN packages="platform-tools platforms;android-37.2 sources;android-37.2 build-tools;36.0.0" \
    && if [ "$TARGETARCH" = "amd64" ]; then packages="$packages extras;google;auto"; fi \
    && yes | sdkmanager --sdk_root="${ANDROID_HOME}" --licenses || true \
    && sdkmanager --sdk_root="${ANDROID_HOME}" --install $packages \
    && chmod -R a+rX "${ANDROID_HOME}" \
    && if [ "$TARGETARCH" = "amd64" ]; then \
        chmod a+x "${ANDROID_HOME}/extras/google/auto/desktop-head-unit"; \
    fi

# AGP 9 downloads its own linux/amd64 aapt2 unless android.aapt2FromMavenOverride
# points at a native binary. Ubuntu's adb replaces the amd64 platform-tools binary.
RUN if [ "$TARGETARCH" = "arm64" ]; then \
        set -eu; \
        dest="${ANDROID_HOME}/build-tools/36.0.0"; \
        tmp="$(mktemp -d)"; \
        for spec in \
            "aapt2 7512ff7e381bea6fd310b6f6e347422c8fda21e07c6e3f0162742e95eb9d7f98" \
            "aidl 8c97356b8bba8f7aad44cfd408e3ee24c66a1258244b5d08af1c9249f50dd659" \
            "zipalign e8856fb24b10095eb6e940c577ce96d89ddbfc45aa0f7eeaef1597ef68f11a12" \
            "split-select fb7f0c3c87dbd4243d7e1277ba64ded2399389244058b6a4c969cc615360766c"; do \
            tool="${spec%% *}"; \
            sum="${spec#* }"; \
            curl -fsSL -o "${tmp}/${tool}" "${ARM64_BUILD_TOOLS_URL}/${tool}"; \
            echo "${sum}  ${tmp}/${tool}" | sha256sum -c -; \
            install -m 0755 "${tmp}/${tool}" "${dest}/${tool}"; \
        done; \
        rm -rf "$tmp"; \
        ln -sfn /usr/bin/adb "${ANDROID_HOME}/platform-tools/adb"; \
        mkdir -p /home/vscode/.gradle; \
        printf 'android.aapt2FromMavenOverride=%s\n' "${dest}/aapt2" > /home/vscode/.gradle/gradle.properties; \
        chown -R vscode:vscode /home/vscode/.gradle; \
    fi
