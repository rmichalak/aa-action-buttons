#!/usr/bin/env python3
"""Check that main can be released, then write the following patch bump.

check reads version.properties and the released tags. It refuses when versionCode
is not higher than the last release, and records VERSION_NAME for the workflow.

bump runs after a successful publish. It raises the patch and versionCode by 1
and pushes that commit to main.
"""

import os
import re
import subprocess
import sys

VERSION_NAME_PATTERN = re.compile(r"[0-9]+\.[0-9]+\.[0-9]+")
TAG_PATTERN = re.compile(r"v[0-9]+\.[0-9]+\.[0-9]+")
VERSION_FILE_HEADER = (
    "# versionName is the next release. The release workflow ships it, then raises the patch and versionCode by 1.\n"
    "# For a minor or major release, edit both fields in a normal commit. versionCode must be higher than the last release.\n"
)


def fail(message):
    print(message, file=sys.stderr)
    sys.exit(1)


def run(*args, check=True):
    result = subprocess.run(args, check=False, capture_output=True, text=True)
    if check and result.returncode != 0:
        fail(result.stderr.strip() or result.stdout.strip() or f"{args[0]} failed.")
    return result


def read_version_text(text, label):
    code = None
    name = None
    try:
        for raw in text.splitlines():
            line = raw.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            key = key.strip()
            value = value.strip()
            if key == "versionCode":
                code = int(value)
            elif key == "versionName":
                name = value
    except ValueError as exc:
        fail(f"{label} has an invalid versionCode: {exc}")
    if code is None or name is None:
        fail(f"{label} must define versionCode and versionName.")
    if not VERSION_NAME_PATTERN.fullmatch(name):
        fail(f"{label} versionName must match x.y.z (got {name!r}).")
    return code, name


def read_version_file():
    try:
        with open("version.properties", encoding="utf-8") as fh:
            text = fh.read()
    except OSError as exc:
        fail(f"Could not read version.properties: {exc}")
    return read_version_text(text, "version.properties")


def ensure_tag(tag):
    have = run("git", "rev-parse", "--verify", "--quiet", f"refs/tags/{tag}", check=False)
    if have.returncode == 0:
        return
    run("git", "fetch", "origin", f"refs/tags/{tag}:refs/tags/{tag}")


def highest_released_code():
    listed = run("git", "ls-remote", "--tags", "origin")
    highest_code = None
    highest_tag = None
    for line in listed.stdout.splitlines():
        if "\t" not in line:
            continue
        ref = line.split("\t", 1)[1].strip()
        if not ref.startswith("refs/tags/") or ref.endswith("^{}"):
            continue
        tag = ref.removeprefix("refs/tags/")
        if not TAG_PATTERN.fullmatch(tag):
            continue
        ensure_tag(tag)
        shown = run("git", "show", f"{tag}:version.properties")
        code, _ = read_version_text(shown.stdout, f"Tag {tag}")
        if highest_code is None or code > highest_code:
            highest_code = code
            highest_tag = tag
    return highest_code, highest_tag


def check():
    current_code, current_name = read_version_file()
    highest_code, highest_tag = highest_released_code()
    if highest_code is not None and current_code <= highest_code:
        fail(
            f"versionCode {current_code} must be greater than {highest_code} "
            f"already released on {highest_tag}."
        )
    env_path = os.environ.get("GITHUB_ENV", "")
    if env_path:
        with open(env_path, "a", encoding="utf-8") as out:
            out.write(f"VERSION_NAME={current_name}\n")
    print(f"Releasing {current_name} (versionCode {current_code}).")


def bump():
    current_code, current_name = read_version_file()
    major, minor, patch = (int(part) for part in current_name.split("."))
    next_name = f"{major}.{minor}.{patch + 1}"
    next_code = current_code + 1
    with open("version.properties", "w", encoding="utf-8") as fh:
        fh.write(VERSION_FILE_HEADER)
        fh.write(f"versionCode={next_code}\n")
        fh.write(f"versionName={next_name}\n")

    run("git", "config", "--local", "user.name", "github-actions[bot]")
    run("git", "config", "--local", "user.email", "41898282+github-actions[bot]@users.noreply.github.com")
    run("git", "add", "version.properties")
    run("git", "commit", "-m", f"Bump version to {next_name}", "-m", f"versionCode {next_code}")
    run("git", "push", "origin", "HEAD:main")
    print(f"Advanced main to {next_name} (versionCode {next_code}).")


def main():
    command = sys.argv[1] if len(sys.argv) == 2 else ""
    if command == "check":
        check()
    elif command == "bump":
        bump()
    else:
        fail("usage: scripts/release-version.py check|bump")


if __name__ == "__main__":
    main()
