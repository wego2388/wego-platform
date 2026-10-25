#!/usr/bin/env python3
"""Install the owner-approved public GSC proof; reload the gateway only.

Run as root on the VPS with the repository proof file as the sole argument.
Refuse config drift, test a disposable clone before replacing live config,
preserve rollback files, and require all existing container identities stable.
No env, credentials, database or application image is read or changed.
"""
import fcntl
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

GATEWAY = "resort-os-prod-nginx-1"
ROOT = Path("/srv/safari-tours-sharm/shared/gateway")
PROOF = "googlee21c400d256cf31d.html"
BASELINE = "cbfbc1127e00df6f9cbe5dcbeb35879bc3e8ef07e2d69f423bbc7c92964e0a1b"
EXPECTED = f"google-site-verification: {PROOF}\n".encode()
MARKER = "    # Public origin accepts only small JSON bodies (edge limit 64k).\n    client_max_body_size 1m;\n"
ADDITION = f"""
    # Owner-authorized Google Search Console proof for Safari's own account.
    # Exact public path only; the separate staff host never serves this file.
    location = /{PROOF} {{
        default_type text/html;
        alias /etc/nginx/vps-sites/{PROOF};
    }}
"""
ORIGINS = ["https://elkheima.com/", "https://www.elkheima.com/",
           "https://app.elkheima.com/", "https://owner.elkheima.com/",
           "https://safaritourssharm.com/en", "https://staff.safaritourssharm.com/"]


def run(*args):
    return subprocess.check_output(args, text=True).strip()


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def containers():
    ids = run("docker", "ps", "-q").splitlines()
    fmt = '{"name":{{json .Name}},"id":{{json .Id}},"image":{{json .Image}},"started":{{json .State.StartedAt}},"restarts":{{json .RestartCount}}}'
    objects = [json.loads(line) for line in run("docker", "inspect", "--format", fmt, *ids).splitlines()]
    return {c.pop("name"): c for c in objects}


def codes():
    return {url: run("curl", "--silent", "--show-error", "--location",
                     "--max-time", "20", "--output", "/dev/null",
                     "--write-out", "%{http_code}", url) for url in ORIGINS}


def atomic_copy(source, target):
    temp = target.with_name(target.name + ".gsc-new")
    shutil.copyfile(source, temp)
    os.chmod(temp, 0o644)
    os.replace(temp, target)


def main():
    if os.geteuid() != 0 or len(sys.argv) != 2:
        raise SystemExit("Run as root with the approved proof file argument")
    source = Path(sys.argv[1])
    if source.read_bytes() != EXPECTED:
        raise SystemExit("Proof file does not match owner-approved Google token")
    locks = []
    for name in ["site-release.lock", "safari-env.lock"]:
        handle = open(ROOT.parent / name, "a")
        fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        locks.append(handle)
    config = ROOT / "safari.conf"
    if digest(config) != BASELINE or (ROOT / PROOF).exists():
        raise SystemExit("Gateway drift or existing proof; inspect before retry")
    old = config.read_text()
    if old.count(MARKER) != 1:
        raise SystemExit("Public server marker is not unique")
    before = containers()
    before_codes = codes()
    # The unrelated legacy Resort staff origin already returns 503 before
    # this operation. Preserve that baseline and report it, never repair or
    # claim it healthy as part of a Safari public verification-file change.
    expected_codes = {url: "200" for url in ORIGINS}
    expected_codes["https://app.elkheima.com/"] = "503"
    if before_codes != expected_codes:
        raise SystemExit(f"Preflight HTTPS not healthy: {before_codes}")
    stage = Path(tempfile.mkdtemp(prefix="google-proof-", dir=ROOT.parent))
    os.chmod(stage, 0o700)
    candidate = stage / "gateway"
    shutil.copytree(ROOT, candidate)
    shutil.copyfile(config, stage / "safari.conf.before")
    (candidate / "safari.conf").write_text(old.replace(MARKER, MARKER + ADDITION))
    shutil.copyfile(source, candidate / PROOF)
    os.chmod(candidate / PROOF, 0o644)
    fmt = '{"Image":{{json .Image}},"Mounts":{{json .Mounts}},"Networks":{{json .NetworkSettings.Networks}}}'
    gateway = json.loads(run("docker", "inspect", "--format", fmt, GATEWAY))
    test_cmd = ["docker", "run", "--rm"]
    for network in sorted(gateway["Networks"]):
        test_cmd += ["--network", network]
    for mount in gateway["Mounts"]:
        src = str(candidate) if mount["Destination"] == "/etc/nginx/vps-sites" else mount["Source"]
        test_cmd += ["--mount", f"type=bind,src={src},dst={mount['Destination']},readonly"]
    test_cmd += ["--entrypoint", "nginx", gateway["Image"], "-t"]
    run(*test_cmd)
    if containers() != before or digest(config) != BASELINE:
        raise SystemExit("Concurrent deployment detected before install")
    changed = False
    try:
        atomic_copy(candidate / PROOF, ROOT / PROOF)
        atomic_copy(candidate / "safari.conf", config)
        changed = True
        run("docker", "exec", GATEWAY, "nginx", "-t")
        run("docker", "exec", GATEWAY, "nginx", "-s", "reload")
        body = run("curl", "--fail", "--silent", "--show-error", "--retry", "3",
                   "--retry-delay", "1", "--retry-all-errors", "--max-time", "15",
                   f"https://safaritourssharm.com/{PROOF}")
        if body != EXPECTED.decode().strip():
            raise RuntimeError("Live proof mismatch")
        after_codes = codes()
        if after_codes != before_codes or containers() != before:
            raise RuntimeError("Protected endpoints or container identities drifted")
        report = {"status": "PASS", "proof_url": f"https://safaritourssharm.com/{PROOF}",
                  "gateway_before": BASELINE, "gateway_after": digest(config),
                  "protected_containers": len(before), "https": after_codes,
                  "rollback_config": str(stage / "safari.conf.before"),
                  "preexisting_warning": "app.elkheima.com returned 503 before and after; unchanged"}
        (stage / "evidence.json").write_text(json.dumps(report, indent=2) + "\n")
        print(json.dumps(report))
    except Exception:
        if changed:
            atomic_copy(stage / "safari.conf.before", config)
            run("docker", "exec", GATEWAY, "nginx", "-t")
            run("docker", "exec", GATEWAY, "nginx", "-s", "reload")
        raise


if __name__ == "__main__":
    main()
