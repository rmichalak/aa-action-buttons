# linux/amd64: the Desktop Head Unit package is published only for that architecture.
FROM --platform=linux/amd64 mcr.microsoft.com/devcontainers/base:ubuntu-24.04

ENV ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_HOME=/opt/android-sdk \
    JAVA_HOME=/usr/lib/jvm/java-21 \
    PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:/opt/android-sdk/extras/google/auto:${PATH}

ARG CMDTOOLS_REV=15859902

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        openjdk-21-jdk-headless \
        unzip \
        libc++1 \
        libc++abi1 \
        libusb-1.0-0 \
        libgl1 \
        libx11-6 \
        libxi6 \
        libxext6 \
        libxrender1 \
    && rm -rf /var/lib/apt/lists/* \
    && ln -sfn /usr/lib/jvm/java-21-openjdk-amd64 /usr/lib/jvm/java-21

RUN curl -fsSL -o /tmp/cmdline-tools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-${CMDTOOLS_REV}_latest.zip" \
    && mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools \
    && rm /tmp/cmdline-tools.zip \
    && mv /tmp/cmdline-tools/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest"

RUN yes | sdkmanager --sdk_root="${ANDROID_HOME}" --licenses || true \
    && sdkmanager --sdk_root="${ANDROID_HOME}" --install \
        "platform-tools" \
        "platforms;android-37.2" \
        "sources;android-37.2" \
        "build-tools;36.0.0" \
        "extras;google;auto" \
    && chmod -R a+rX "${ANDROID_HOME}" \
    && chmod a+x "${ANDROID_HOME}/extras/google/auto/desktop-head-unit"
