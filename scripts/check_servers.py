"""Run the compatibility probe on disposable Paper servers outside the project folder.

Requires Python 3, Maven, and JDK 17/21/25. Downloaded server artifacts are checked
against Paper's published SHA-256 checksums. No production server is accessed.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import tempfile
import urllib.request
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
TARGET = Path(os.environ.get("JAIL_VERIFY_DIR", str(Path(tempfile.gettempdir()) / "JailReloaded-verification")))
VERSIONS = ["1.19", "1.19.4", "1.20.6", "1.21.11", "26.1.2", "26.2", "26.3"]


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "JailReloaded-Compatibility/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def run(command, logfile, **kwargs):
    with logfile.open("w", encoding="utf-8") as log:
        subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--versions", nargs="+", default=VERSIONS)
    parser.add_argument("--accept-eula", action="store_true", help="Accept Mojang's EULA for these test servers")
    parser.add_argument("--vault-jar", type=Path, help="Optional Vault plugin JAR for economy bridge tests")
    parser.add_argument("--mysql-port", type=int, help="Optional disposable MySQL/MariaDB on localhost; see README for environment variables")
    parser.add_argument("--report-file", type=Path, default=TARGET / "results.json")
    args = parser.parse_args()
    if not args.accept_eula:
        parser.error("Review https://aka.ms/MinecraftEULA and supply --accept-eula to run Minecraft servers")
    TARGET.mkdir(parents=True, exist_ok=True)
    mvn = shutil.which("mvn.cmd") or shutil.which("mvn")
    run([mvn, "-B", "package"], TARGET / "package.log")
    cpfile = TARGET / "classpath.txt"
    run([mvn, "-B", "dependency:build-classpath", "-Dmdep.includeScope=compile", f"-Dmdep.outputFile={cpfile}"], TARGET / "classpath.log")
    cp = cpfile.read_text().strip() + os.pathsep + str(ROOT / "target/classes")
    probe_classes = TARGET / "probe-classes"
    probe_classes.mkdir(exist_ok=True)
    run(["javac", "--release", "17", "-cp", cp, "-d", str(probe_classes),
         str(ROOT / "src/compatibility/java/com/graywolf336/jail/CompatibilityProbe.java")], TARGET / "probe-compile.log")
    probe_jar = TARGET / "CompatibilityProbe.jar"
    with zipfile.ZipFile(probe_jar, "w") as jar:
        jar.writestr("plugin.yml", "name: CompatibilityProbe\nversion: '1.0'\nmain: com.graywolf336.jail.CompatibilityProbe\napi-version: '1.19'\ndepend: [JailReloaded]\n")
        for file in probe_classes.rglob("*.class"):
            jar.write(file, file.relative_to(probe_classes).as_posix())
    version = ET.parse(ROOT / "pom.xml").getroot().find("{http://maven.apache.org/POM/4.0.0}version").text
    plugin_jar = ROOT / "target" / f"JailReloaded-{version}.jar"
    results = []
    for version in args.versions:
        folder = TARGET / version
        folder.mkdir(exist_ok=True)
        builds = json.loads(fetch(f"https://fill.papermc.io/v3/projects/paper/versions/{version}/builds"))
        build = next((item for item in builds if item["channel"] == "STABLE"), builds[0])
        download = build["downloads"]["server:default"]
        server_jar = folder / "server.jar"
        if not server_jar.exists() or hashlib.sha256(server_jar.read_bytes()).hexdigest() != download["checksums"]["sha256"]:
            server_jar.write_bytes(fetch(download["url"]))
        assert hashlib.sha256(server_jar.read_bytes()).hexdigest() == download["checksums"]["sha256"]
        (folder / "build.json").write_text(json.dumps(build, indent=2))
        java_version = "17" if version.startswith("1.19") else "21" if version.startswith("1.") else "25.0.3"
        java = Path(os.environ.get(f"JAVA{java_version.split('.')[0]}_HOME", f"C:/Program Files/Java/jdk-{java_version}")) / "bin/java.exe"
        if not java.exists():
            raise FileNotFoundError(f"Set JAVA{java_version.split('.')[0]}_HOME to a matching JDK")
        plugins = folder / "plugins"
        plugins.mkdir(exist_ok=True)
        shutil.copy2(plugin_jar, plugins / "JailReloaded.jar")
        shutil.copy2(probe_jar, plugins / "CompatibilityProbe.jar")
        if args.vault_jar:
            shutil.copy2(args.vault_jar, plugins / "Vault.jar")
        elif (plugins / "Vault.jar").exists():
            (plugins / "Vault.jar").rename(plugins / f"Vault.jar.disabled-{time.time_ns()}")
        # Repeatable clean data without deleting worlds or downloaded artifacts.
        data = plugins / "JailReloaded"
        if data.exists():
            data.rename(plugins / f"JailReloaded.previous-{time.time_ns()}")
        data.mkdir()
        with zipfile.ZipFile(plugin_jar) as jar:
            config = jar.read("config.yml").decode().replace("notification: true", "notification: false")
        (data / "config.yml").write_text(config)
        (folder / "eula.txt").write_text("eula=true\n")
        (folder / "server.properties").write_text("server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nlevel-type=minecraft:flat\ngenerate-structures=false\n")
        logpath = folder / "probe.log"
        print(f"Starting Paper {version} build {build['id']} on Java {java_version}", flush=True)
        with logpath.open("w", encoding="utf-8") as log:
            java_args = [str(java), "-Xms256M", "-Xmx1G"]
            if args.mysql_port:
                java_args += [f"-Djail.probe.mysql.port={args.mysql_port}"]
            process = subprocess.Popen([*java_args, "-jar", "server.jar", "--nogui"],
                                       cwd=folder, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT,
                                       creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            try:
                process.wait(timeout=240)
            except subprocess.TimeoutExpired:
                process.stdin.write(b"stop\n"); process.stdin.flush()
                try:
                    process.wait(timeout=30)
                except subprocess.TimeoutExpired:
                    process.kill(); process.wait()
                raise RuntimeError(f"Paper {version} timed out; see {logpath}")
        output = logpath.read_text(encoding="utf-8", errors="replace")
        plugin_errors = "Jail Error!!!" in output or any(
            "JailReloaded" in line and any(marker in line for marker in (
                "Could not pass event", "generated an exception", "Error occurred while", " ERROR]", " SEVERE]"))
            for line in output.splitlines())
        console_checks = {
            "banner_once": output.count("J A I L - R E L O A D E D") == 1,
            "ready_once": output.count("[JailReloaded] Ready in ") == 1,
            "startup_summary": "Storage: SQLite | jail3.sqlite" in output and "API baseline: 1.19" in output,
            "player_actions": all(action + " | player=" in output for action in ("JAILED", "TRANSFERRED", "RELEASED")),
            "shutdown_summary": "[JailReloaded] Disabled | saved " in output,
        }
        passed = "COMPATIBILITY PASS:" in output and "COMPATIBILITY FAIL" not in output and not plugin_errors and all(console_checks.values())
        result = {"version": version, "build": build["id"], "java": java_version,
                  "passed": passed, "checks": output.count("CHECK PASS:"), "log": str(logpath),
                  "vault": bool(args.vault_jar), "mysql": bool(args.mysql_port),
                  "console_checks": console_checks,
                  "plugin_sha256": hashlib.sha256(plugin_jar.read_bytes()).hexdigest()}
        results.append(result)
        args.report_file.write_text(json.dumps(results, indent=2))
        print(json.dumps(result), flush=True)
        if not passed:
            raise RuntimeError(f"Paper {version} failed; see {logpath}")


if __name__ == "__main__":
    main()
