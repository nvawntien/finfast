"""Verify the POM's executable backend JAR without running the application.

Run after Maven package; uses only Python's standard library. No datasource or
environment files are loaded, and archive contents are never extracted.
"""

from pathlib import Path
import sys
import xml.etree.ElementTree as ET
from zipfile import BadZipFile, ZipFile


BACKEND = Path(__file__).resolve().parents[1] / "backend"


def main():
    pom = ET.parse(BACKEND / "pom.xml").getroot()
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    if pom.findtext("m:packaging", namespaces=namespace) != "jar":
        raise ValueError("Expected the checked-in JAR packaging")
    final_name = pom.findtext("m:build/m:finalName", namespaces=namespace)
    if not final_name or Path(final_name).name != final_name:
        raise ValueError("Expected an explicit build/finalName in pom.xml")
    jar = BACKEND / "target" / f"{final_name}.jar"
    if not jar.is_file() or jar.stat().st_size == 0:
        raise ValueError(f"Missing or empty executable JAR: {jar.name}")

    test_classes = {
        path.relative_to(BACKEND / "target/test-classes").as_posix()
        for path in (BACKEND / "target/test-classes").rglob("*.class")
    }
    if not test_classes:
        raise ValueError("Expected compiled test classes; do not use maven.test.skip")

    with ZipFile(jar) as archive:
        entries = set(archive.namelist())
        manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8").splitlines()
        for attribute in (
            "Main-Class: org.springframework.boot.loader.launch.JarLauncher",
            "Start-Class: com.finfast.FinfastApplication",
        ):
            if attribute not in manifest:
                raise ValueError(f"Missing executable manifest attribute: {attribute}")
        for entry in (
            "org/springframework/boot/loader/launch/JarLauncher.class",
            "BOOT-INF/classes/com/finfast/FinfastApplication.class",
        ):
            if entry not in entries:
                raise ValueError(f"Missing executable JAR entry: {entry}")
        leaked_classes = entries & {f"BOOT-INF/classes/{name}" for name in test_classes}
        test_libraries = {
            name for name in entries
            if name.startswith(("BOOT-INF/lib/archunit-", "BOOT-INF/lib/junit-",
                                "BOOT-INF/lib/testcontainers-"))
        }
        if leaked_classes or test_libraries:
            raise ValueError("Test classes or test-only libraries found in executable JAR")

    print(f"PASS: {jar.name} is executable; no compiled test classes or test-only libraries packaged.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, KeyError, BadZipFile, ET.ParseError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
