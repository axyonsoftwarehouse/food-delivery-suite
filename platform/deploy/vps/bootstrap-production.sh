#!/usr/bin/env bash
set -Eeuo pipefail

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get -y upgrade
apt-get install -y \
  ca-certificates curl gnupg ufw fail2ban unattended-upgrades \
  docker.io docker-compose-v2 postgresql-client redis-tools

systemctl enable --now docker fail2ban unattended-upgrades

# Create a non-root administrator and preserve the existing public key.
id -u deploy >/dev/null 2>&1 || useradd --create-home --shell /bin/bash --groups sudo,docker deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
cp /root/.ssh/authorized_keys /home/deploy/.ssh/authorized_keys
chown deploy:deploy /home/deploy/.ssh/authorized_keys
chmod 600 /home/deploy/.ssh/authorized_keys

# Disable password and root SSH after the deploy account has the key.
cat >/etc/ssh/sshd_config.d/99-production-hardening.conf <<'EOF'
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
ChallengeResponseAuthentication no
PubkeyAuthentication yes
X11Forwarding no
AllowUsers deploy
EOF
sshd -t
systemctl reload ssh

# Host firewall: only administration and web traffic are reachable publicly.
ufw default deny incoming
ufw default allow outgoing
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

# Fail2ban only needs to protect SSH; application traffic will be rate-limited at Cloudflare.
cat >/etc/fail2ban/jail.d/sshd.local <<'EOF'
[sshd]
enabled = true
maxretry = 5
findtime = 10m
bantime = 1h
EOF
systemctl restart fail2ban

# Automated security updates are enabled; reboot only when explicitly scheduled later.
cat >/etc/apt/apt.conf.d/52unattended-upgrades-local <<'EOF'
Unattended-Upgrade::Automatic-Reboot "false";
EOF

# Create an isolated application stack. Database and cache have no host ports.
install -d -m 750 -o deploy -g deploy /opt/production
POSTGRES_PASSWORD="$(openssl rand -base64 36 | tr -d '\n')"
cat >/opt/production/.env <<EOF
POSTGRES_DB=platform
POSTGRES_USER=platform
POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
EOF
chmod 640 /opt/production/.env
chown deploy:deploy /opt/production/.env

cat >/opt/production/compose.yaml <<'EOF'
services:
  postgres:
    image: postgres:16-alpine
    restart: unless-stopped
    env_file: .env
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $$POSTGRES_USER -d $$POSTGRES_DB"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks: [internal]

  pgbouncer:
    image: edoburu/pgbouncer:1.24.1-p0
    restart: unless-stopped
    env_file: .env
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      POOL_MODE: transaction
      MAX_CLIENT_CONN: 100
      DEFAULT_POOL_SIZE: 15
    depends_on:
      postgres:
        condition: service_healthy
    networks: [internal]

  redis:
    image: redis:7-alpine
    restart: unless-stopped
    command: ["redis-server", "--appendonly", "yes"]
    volumes:
      - redis_data:/data
    networks: [internal]

networks:
  internal:
    internal: true

volumes:
  postgres_data:
  redis_data:
EOF
chown deploy:deploy /opt/production/compose.yaml

sudo -u deploy docker compose --project-directory /opt/production up -d
EOF
