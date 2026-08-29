# Wego Platform — Dev Environment Reference

Ubuntu 24.04 LTS (Noble Numbat) is the confirmed working OS for this project.
Use it on any new machine to replicate the current environment exactly.

---

## Operating System

**Ubuntu 24.04.4 LTS (Noble Numbat)**
Download: https://releases.ubuntu.com/24.04/

---

## Required Tools

### JDK 25 (Temurin)

The project requires exactly JDK 25. Java 17 exists on the current machine at
`/home/wego/jdk17` but is not on PATH and is not used by any build task.
jOOQ 3.21 requires Java 21+, and the approved baseline is Java 25.

```bash
# Install SDKMAN first
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install Temurin 25 (same distribution used in CI)
sdk install java 25.0.2-tem

# Verify
java -version        # must show 25
javac -version       # must show 25
```

Set permanently in `~/.bashrc`:
```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
export PATH="$JAVA_HOME/bin:$PATH"
```

Pinned in: `.java-version` → `25`
CI uses: `actions/setup-java` with `distribution: temurin`, `java-version: "25"`

---

### Node.js 24.19.0

The project requires Node 24 LTS. The host default on the current machine is
Node 20.20.0 at `/usr/bin/node` — it is never used for any build task.
Node 24.19.0 is installed at `~/.local/node24/` and used for all web and
foundry commands.

```bash
# Install NVM
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
source ~/.nvm/nvm.sh

# Install the pinned version
nvm install 24.19.0
nvm alias default 24.19.0

# Verify
node --version    # must show v24.19.0
```

Pinned in: `.nvmrc` → `24.19.0`
Package engines: `"node": ">=24.11.0 <25"`

---

### pnpm 10.34.4

Used for all web workspace and foundry package management.

```bash
corepack enable
corepack prepare pnpm@10.34.4 --activate

# Verify
pnpm --version    # must show 10.34.4
```

Pinned in: `web/package.json` → `"packageManager": "pnpm@10.34.4"`

---

### Docker 29+ and Docker Compose v5+

All infrastructure runs in containers: PostgreSQL 18.4, Redis 8.10,
Spring Boot backend, and Nginx edge. Testcontainers also uses Docker for
backend integration tests.

```bash
# Install Docker Engine
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
newgrp docker

# Verify
docker --version           # 29.x on current machine
docker compose version     # v5.x on current machine
```

Current machine: Docker 29.2.1 / Compose v5.0.2

---

### Git 2.43+

```bash
sudo apt-get install git

# Verify
git --version    # 2.43.0 on current machine
```

---

### Gradle (no installation needed)

Gradle 9.5.0 is embedded in the repository via the Gradle Wrapper.
It downloads automatically on first use and verifies the download with SHA-256.

```bash
# From the project root — downloads Gradle 9.5.0 automatically
./gradlew --version

# SHA-256 pinned in gradle/wrapper/gradle-wrapper.properties:
# 553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746
```

No global Gradle installation required or accepted.

---

## Verification — Run After Setup

These commands confirm the environment is fully ready for the project:

```bash
# 1. Java
java -version                                      # openjdk 25

# 2. Node
node --version                                     # v24.19.0

# 3. pnpm
pnpm --version                                     # 10.34.4

# 4. Docker
docker info                                        # running
docker compose version                             # v5+

# 5. Backend build
./gradlew :platform:application:check

# 6. Mobile build
./gradlew :mobile:shared:check :mobile:apps:ops:check :mobile:apps:customer:check

# 7. Web build
corepack enable
pnpm --dir web install --frozen-lockfile
pnpm --dir web run check

# 8. Foundry validation
pnpm --dir foundry install --frozen-lockfile
pnpm --dir foundry run validate

# 9. Infrastructure stack
docker compose --env-file .env.example \
  -f infrastructure/compose/compose.yaml \
  up --build --wait
curl --fail http://127.0.0.1:58080/healthz
docker compose --env-file .env.example \
  -f infrastructure/compose/compose.yaml down
```

---

## Deferred Tools — Do Not Install Yet

These tools are not needed in the current foundation phase (WEGO-000).
Install them only when the corresponding product slice requires them.

| Tool | Reason deferred |
|------|-----------------|
| Android Studio | Mobile is JVM-only compilation today; no Android/iOS release artifact exists yet |
| Xcode (macOS) | Same as above — iOS target is not wired |
| IntelliJ IDEA | Optional IDE; any editor works |
| Redis CLI (standalone) | Redis runs inside Docker Compose |
| PostgreSQL client (psql) | Database runs inside Docker Compose |

---

## Summary Table

| Tool | Required version | Installed on current machine |
|------|-----------------|------------------------------|
| OS | Ubuntu 24.04 LTS | Ubuntu 24.04.4 LTS ✓ |
| JDK (Temurin) | 25 | `/home/wego/jdk17` only — **needs JDK 25** |
| Node.js | 24.19.0 | `~/.local/node24/` ✓ (host `/usr/bin/node` is 20, ignored) |
| pnpm | 10.34.4 | ✓ |
| Docker | 29+ | 29.2.1 ✓ |
| Docker Compose | v5+ | v5.0.2 ✓ |
| Git | 2.x | 2.43.0 ✓ |
| Gradle | 9.5.0 (wrapper) | embedded in repo ✓ |
