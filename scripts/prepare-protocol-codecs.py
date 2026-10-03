#!/usr/bin/env python3
"""Build pinned, unmodified Via codecs. No Minecraft artifacts are inputs or outputs."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile


def verified(path, digest):
    return path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == digest


def run(command, directory):
    subprocess.run(command, cwd=directory, check=True)


def prepare(lock, output, caches, verifier):
    output.mkdir(parents=True, exist_ok=True)
    work = output / "source"
    work.mkdir(exist_ok=True)
    for project, pin in lock.items():
        target = output / (project + ".jar")
        if verified(target, pin["artifactSha256"]):
            continue
        cached = next((path for root in caches for path in root.rglob(project + "-*.jar")
                       if verified(path, pin["artifactSha256"])), None)
        if cached is None:
            source = work / project
            archive = work / (project + ".zip")
            if not verified(archive, pin["sourceSha256"]):
                url = f'https://codeload.github.com/ViaVersion/{project}/zip/{pin["commit"]}'
                with urllib.request.urlopen(url, timeout=120) as response:
                    archive.write_bytes(response.read())
                if not verified(archive, pin["sourceSha256"]):
                    raise ValueError(f"Invalid {project} source digest")
            if not source.exists():
                source.mkdir()
                with zipfile.ZipFile(archive) as zipped:
                    prefix = zipped.namelist()[0].split("/")[0] + "/"
                    for entry in zipped.infolist():
                        relative = entry.filename.removeprefix(prefix)
                        if not relative:
                            continue
                        destination = source / relative
                        if not destination.resolve().is_relative_to(source.resolve()):
                            raise ValueError("Source archive escapes destination")
                        if entry.is_dir():
                            destination.mkdir(parents=True, exist_ok=True)
                        else:
                            destination.parent.mkdir(parents=True, exist_ok=True)
                            destination.write_bytes(zipped.read(entry))
            # Upstream derives its implementation version from the exact git commit.
            if not (source / ".git").exists():
                run(["git", "init", "--quiet"], source)
                run(["git", "fetch", "--quiet", "--depth=1",
                     f"https://github.com/ViaVersion/{project}.git", pin["commit"]], source)
                run(["git", "checkout", "--quiet", "--force", "--detach", "FETCH_HEAD"], source)
            head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip()
            if head != pin["commit"]:
                raise ValueError(f"Unexpected {project} source checkout")
            wrapper = source / "gradlew"
            wrapper.chmod(0o755)
            module = project.lower()
            command = [str(wrapper.resolve()), f":{module}:shadowJar", "--console=plain",
                       "--max-workers=2", "--no-daemon"]
            if project == "ViaBackwards":
                repository = work / "maven"
                version = "5.12.1-cult-transcoder-3a949d70"
                artifact = repository / "com/viaversion/viaversion" / version
                artifact.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(output / "ViaVersion.jar", artifact / f"viaversion-{version}.jar")
                (artifact / f"viaversion-{version}.pom").write_text(
                    "<project><modelVersion>4.0.0</modelVersion><groupId>com.viaversion</groupId>"
                    f"<artifactId>viaversion</artifactId><version>{version}</version></project>\n")
                init = work / "exact-api.init.gradle"
                init.write_text(
                    "settingsEvaluated { settings -> settings.dependencyResolutionManagement.repositories.maven { "
                    f"url = uri('{repository.resolve().as_uri()}'); "
                    "content { includeModule('com.viaversion','viaversion') } } }\n"
                    "beforeProject { project -> project.configurations.configureEach { resolutionStrategy.eachDependency { d -> "
                    "if (d.requested.group == 'com.viaversion' && d.requested.name == 'viaversion') "
                    f"d.useVersion('{version}')" + " } } }\n")
                command.extend(["--init-script", str(init.resolve())])
            run(command, source)
            cached = source / "build/libs" / (project + "-5.12.1-SNAPSHOT.jar")
            if not verified(cached, pin["artifactSha256"]):
                raise ValueError(f"Non-reproducible {project} codec artifact")
        run(["python3", str(verifier), str(cached)], output)
        shutil.copyfile(cached, target)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--lock", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--artifact-cache", type=Path, action="append", default=[])
    args = parser.parse_args()
    prepare(json.loads(args.lock.read_text()), args.output.resolve(), args.artifact_cache,
            Path(__file__).resolve().with_name("verify-no-bundled-minecraft.py"))
