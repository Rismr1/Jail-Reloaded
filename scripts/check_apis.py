"""Compile production sources against every published stable Spigot API from 1.19 through 26.3.

Requires Python 3, Maven and JDK 25 (to read newer API class files).
The distributed plugin is always built separately against the default 1.19 API.
"""
import json
import os
import tempfile
import re
import shutil
import subprocess
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET = Path(os.environ.get("JAIL_VERIFY_DIR", str(Path(tempfile.gettempdir()) / "JailReloaded-verification"))) / "apis"


def main():
    TARGET.mkdir(parents=True, exist_ok=True)
    url = "https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/maven-metadata.xml"
    with urllib.request.urlopen(url, timeout=60) as response:
        metadata = ET.fromstring(response.read())
    versions = []
    for entry in metadata.findall("./versioning/versions/version"):
        version = entry.text.removesuffix("-R0.1-SNAPSHOT")
        if not re.fullmatch(r"(?:1\.(?:19|20|21)|26\.[123])(?:\.\d+)?", version):
            continue
        versions.append((version, entry.text))
    mvn = shutil.which("mvn.cmd") or shutil.which("mvn")
    sources = list((ROOT / "src/main/java").rglob("*.java"))
    results = []
    for version, artifact in versions:
        folder = TARGET / version
        folder.mkdir(exist_ok=True)
        cpfile = folder / "classpath.txt"
        with (folder / "maven.log").open("w") as log:
            subprocess.run([mvn, "-B", "dependency:build-classpath", f"-Dspigot-api.version={artifact}",
                            "-Dmdep.includeScope=compile", f"-Dmdep.outputFile={cpfile}"], cwd=ROOT,
                           stdout=log, stderr=subprocess.STDOUT, check=True)
        classes = folder / "classes"
        classes.mkdir(exist_ok=True)
        with (folder / "javac.log").open("w") as log:
            result = subprocess.run(["javac", "--release", "17", "-cp", cpfile.read_text().strip(),
                                     "-d", str(classes), *map(str, sources)], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        results.append({"version": version, "passed": result.returncode == 0})
        (TARGET / "results.json").write_text(json.dumps(results, indent=2))
        print(f"{version}: {'PASS' if result.returncode == 0 else 'FAIL'}", flush=True)
        if result.returncode:
            raise RuntimeError(f"Compilation failed; see {folder / 'javac.log'}")


if __name__ == "__main__":
    main()
