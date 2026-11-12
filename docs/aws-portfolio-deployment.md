# FitCore AWS portfolio deployment

The frontend preview is ready for an owner-approved deployment. It lets visitors switch between two fictional gyms and browse people, memberships, bookings and exercises. It has no login, API, data entry or server-side state. The full-stack demo is blocked by the current startup guard and the loopback-only cookie gateway. [Current release checklist](multi-tenant-release-checklist.md)

## Architecture and monthly estimate

Use one Lightsail Ubuntu LTS instance in an owner-approved region. Start with the 1 GB Linux public IPv4 bundle for the static preview. For a future full-stack demo, start with 4 GB RAM and 2 vCPUs. Build reviewed images off-host and measure memory use before opening the site. A starting allocation is 256 MB each for Caddy and Node, 1.25 GB for Spring Boot and 768 MB for PostgreSQL, with the rest available to Linux, Docker and bursts.

| Item | Monthly planning estimate |
| --- | --- |
| Static preview, 1 GB Linux public IPv4 | $7, including 40 GB bundled disk and up to 2 TB transfer in regions with the full allowance. |
| Full stack, 4 GB Linux public IPv4 | $24, including 80 GB bundled disk and up to 4 TB transfer in regions with the full allowance. Mumbai and several other regions have half the listed transfer allowance. |
| Attached Lightsail static IPv4 | Included. An address left unattached for over an hour costs $0.005/hour. |
| Budget notifications | $0 for standard AWS Budgets notifications without paid reports. |
| Backup and storage | Rebuild the demo from reviewed artifacts and synthetic fixtures. Budget $0 in AWS snapshot charges with that approach. Optional snapshots cost $0.05 per GB-month. For example, 20–80 GB-month of retained snapshot storage adds $1–$4. Extra copies and retained snapshots accumulate charges. |
| Total | About $7 for the preview or $24–$28 for full stack under these assumptions. Allow for taxes, transfer overages and any new domain or DNS fees. Reuse an owned subdomain. |

Eligible Lightsail features can use Free Tier credits under both Free and Paid plans. AWS says its former Lightsail short-term offers were replaced by credit-based Free Tier access for new Lightsail customers. Confirm the account's plan, actual remaining eligible credit, expiration and regional bundle availability before creating anything. The Free plan ends after six months or credit exhaustion, whichever comes first. A Paid plan can charge for uncovered use. At the estimated full-stack rate, $100 would cover about 3.6–4.2 months of eligible charges, subject to expiration and exclusions. [AWS Lightsail pricing](https://aws.amazon.com/lightsail/pricing/) · [Lightsail billing FAQ](https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-frequently-asked-questions-faq-billing-and-account-management.html) · [Free and Paid plan eligibility for Lightsail](https://aws.amazon.com/free/compute/lightsail-vs-ec2/) · [Free Tier FAQ](https://aws.amazon.com/free/free-tier-faqs/)

On the future full stack, Caddy serves the Vite assets and forwards only `/api` to Node. Use separate internal Compose networks for Caddy → BFF, BFF → Spring Boot and Spring Boot → PostgreSQL. Publish only Caddy's TCP 80/443. Restrict SSH to the owner's CIDR. The BFF and backend must authenticate their private HTTPS peers, with a dedicated origin and CA. Derive the client address at the trusted edge and discard client-supplied forwarding and identity headers. Keep the existing signed request binding, nonce replay protection, HttpOnly cookie ordering, exact-origin and CSRF checks, tenant isolation and server-side aggregation. The existing gateway validates loopback origins and peers, so this hosted topology requires a reviewed adapter and tests.

## Synthetic-only startup and reset proposal

The current backend accepts only the narrow owned disposable setup. An AWS VM, environment flag or copied database marker grants no startup authority. Keep the commercial startup contract and keys separate.

- Use a distinct demo artifact and an owner-approved launcher on the dedicated host. Before any database dial, verify a signed, short-lived portfolio-only authorization against a host-protected trust root. Bind it to the exact host, artifact, configuration, fixture digest, private PostgreSQL service and fresh deployment generation. Verify the actual container and network identity outside the database and supply the only allowed datasource. Reject stale generations, caller-controlled overrides, persistent storage and any external database target. Keep the signing key off the demo host. Fence API traffic and stop the demo runtime when the lease expires, is revoked or can no longer be verified. The owner must approve key custody, the lease and renewal policy, and the launcher trust boundary before implementation.
- Create an isolated, size-limited disposable PostgreSQL store for each generation. Apply the existing clean migrations through V10 with a separate migrator. Use a restricted runtime role and verified TLS on the internal connection. The only seed source is the approved two-gym fixture. Disable imports, uploads, public registration, email delivery and free-text collection from visitors. Generate unique synthetic account credentials outside the frontend bundle. Keep owners and trainers operator-only. Give reviewers short-lived, individually issued member access to bounded booking actions. Apply limits by peer and account, cap concurrent visitors and requests, and preserve same-gym reference checks. Public visitors can use the read-only preview without credentials.
- Keep reset as an owner-only operation over SSH. Verify the dedicated host, Compose project, current generation and its storage ownership before changing anything. Close public admission and stop writers, revoke the generation, discard only its disposable store, and create a fresh store from the reviewed fixture. Rotate credentials and session keys, then check both gyms and verify old cookies fail before reopening. A failed reset leaves the preview available and the API closed. Keep replayable fixture and image digests off-host. Leave automatic snapshots off for a disposable demo. An optional snapshot needs separate retention, cost and secret-handling approval.

The local test models a verified host authorization and rejects expired or absent permits, wrong scope, changed target or generation, durable storage, public ports, mail and visitor data ingress. It also checks that the current Spring guard rejects portfolio settings. The two-gym fixture was loaded into two fresh owned disposable schemas, checked through PostgreSQL aggregate queries and rejected a cross-gym trainer assignment. These tests exercise the policy and fixture. The trusted launcher, hosted gateway, runtime admission and destructive reset are still implementation and approval blockers. [Fixture and policy test](../gym-backend/src/test/java/com/fitcore/gym/tenancy/PortfolioDemoContractTest.java)

Before hosting the full stack, exercise the real launcher, signed authorization and expiry fence against owned disposable resources. Verify both gyms through the hosted-style gateway, including login, CSRF, role and data boundaries, aggregate results, quotas and session revocation after reset. Check the external firewall and certificate paths before opening review access.

### Reproduce the local checks

Run from the repository root. The backend command uses the existing owned-disposable harness. The preview uses a separate Compose project bound to loopback.

```bash
./gym-backend/scripts/verify-tenancy.sh -Dtest=PortfolioDemoContractTest,FoundationSafetyTest,CertifiedStartupContractTest
(cd gym-frontend && ./node_modules/.bin/vite build --config portfolio/vite.config.js)
(
  set -eu
  export FITCORE_PREVIEW_ADDRESS=:80 FITCORE_PREVIEW_HTTP=127.0.0.1:18880 FITCORE_PREVIEW_HTTPS=127.0.0.1:18446
  preview_dir=$(mktemp -d "$PWD/output/portfolio/local-preview.XXXXXX")
  cp deploy/portfolio/compose.preview.yaml deploy/portfolio/Caddyfile "$preview_dir/"
  cp -R output/portfolio/site "$preview_dir/site"
  compose() { docker compose --env-file /dev/null --project-name fitcore-portfolio-preview-local -f "$preview_dir/compose.preview.yaml" "$@"; }
  test -z "$(docker ps -aq --filter label=com.docker.compose.project=fitcore-portfolio-preview-local)" || exit 1
  trap 'playwright-cli -s=fitcore-portfolio-preview-local close >/dev/null 2>&1 || true; compose down --volumes' EXIT
  compose up -d
  curl --fail --retry 10 --retry-connrefused --silent --head http://127.0.0.1:18880/ || exit 1
  playwright-cli -s=fitcore-portfolio-preview-local open http://127.0.0.1:18880/ || exit 1
  playwright-cli -s=fitcore-portfolio-preview-local run-code --filename=gym-frontend/portfolio/verify-preview.pw.js
)
```

## Commands for later owner-approved execution

Run these in Bash. Check the AWS account, region, domain, spend limit and deletion date with the owner first. Use an existing dedicated AWS CLI profile and SSH key. These commands provision only the static preview. The future backend requires its separately approved artifact, launcher, networking and reset implementation.

**Account and regional price check.** Choose the active Linux public IPv4 bundle with the expected price and an Ubuntu LTS blueprint from the displayed inventory. Check the credit terms in Billing as well. [Free Tier CLI](https://docs.aws.amazon.com/cli/latest/reference/freetier/get-account-plan-state.html) · [Lightsail bundle CLI](https://docs.aws.amazon.com/cli/latest/reference/lightsail/get-bundles.html)

```bash
export AWS_PROFILE=fitcore-portfolio
export AWS_REGION=us-east-1

aws sts get-caller-identity
aws freetier get-account-plan-state --region us-east-1 \
  --query '{Plan:accountPlanType,Status:accountPlanStatus,Credits:accountPlanRemainingCredits,Expires:accountPlanExpirationDate}'
aws lightsail get-bundles \
  --query 'bundles[?isActive && publicIpv4AddressCount==`1` && contains(supportedPlatforms,`"LINUX_UNIX"`) && (ramSizeInGb==`1` || ramSizeInGb==`4`)].{Bundle:bundleId,USD:price,RAM:ramSizeInGb,Disk:diskSizeInGb,Transfer:transferPerMonthInGb}'
aws lightsail get-blueprints \
  --query 'blueprints[?isActive && contains(blueprintId,`"ubuntu"`)].{ID:blueprintId,Name:name}'
aws lightsail get-regions --include-availability-zones \
  --query 'regions[?name==`"us-east-1"`].availabilityZones[].zoneName'
```

**Build the preview bundle locally** from the repository root with Node 22.12 or later in the 22.x series. The archive contains only static output and the preview Caddy and Compose files. The server mounts the bundle’s `site` directory read-only with a fixed path.

```bash
set -eu
(cd gym-frontend && npm ci && ./node_modules/.bin/vite build --config portfolio/vite.config.js)
python3 - <<'PACK'
import tarfile
from pathlib import Path

output = Path('output/portfolio')
with tarfile.open(output / 'fitcore-preview.tgz', 'w:gz') as archive:
    for config in ('compose.preview.yaml', 'Caddyfile'):
        archive.add(Path('deploy/portfolio') / config, arcname=config)
    archive.add(output / 'site', arcname='site')
PACK
shasum -a 256 output/portfolio/fitcore-preview.tgz
```

**After approval, create the budget and instance.** Set `EXPECTED_ACCOUNT`, `BUDGET_EMAIL`, `BUNDLE`, `BLUEPRINT`, `AZ`, `KEY_PAIR` and `SSH_CIDR` to the reviewed values. Use the $7 bundle for this preview. The account-wide cost alert excludes credits and catches leftover resources. Alerts can lag and do not cap charges. Check the bill weekly and schedule deletion before credit expiry or the approved cutoff. If setup stops partway through, inventory the named instance and IP and use the shutdown instructions for any resources already created. [AWS Budgets](https://docs.aws.amazon.com/cost-management/latest/userguide/budgets-best-practices.html) · [Lightsail instance CLI](https://docs.aws.amazon.com/cli/latest/reference/lightsail/create-instances.html) · [Lightsail firewall CLI](https://docs.aws.amazon.com/cli/latest/reference/lightsail/put-instance-public-ports.html)

```bash
set -eu
: "${EXPECTED_ACCOUNT:?}" "${BUDGET_EMAIL:?}" "${BUNDLE:?}" "${BLUEPRINT:?}" "${AZ:?}" "${KEY_PAIR:?}" "${SSH_CIDR:?}"
test "$(aws sts get-caller-identity --query Account --output text)" = "$EXPECTED_ACCOUNT"
[[ "$BUNDLE" =~ ^[a-zA-Z0-9_-]+$ ]]
test "$(aws lightsail get-bundles --query "bundles[?bundleId=='$BUNDLE' && isActive && publicIpv4AddressCount==\`1\` && ramSizeInGb==\`1\` && price==\`7\` && contains(supportedPlatforms, 'LINUX_UNIX')] | length(@)" --output text)" = 1
INSTANCE=fitcore-portfolio-preview
STATIC_IP=fitcore-portfolio-preview-ip
NOTICES=$(python3 - "$BUDGET_EMAIL" <<'PY'
import json, sys
print(json.dumps([
    {"Notification": {"NotificationType": kind, "ComparisonOperator": "GREATER_THAN",
                      "Threshold": amount, "ThresholdType": "ABSOLUTE_VALUE"},
     "Subscribers": [{"SubscriptionType": "EMAIL", "Address": sys.argv[1]}]}
    for kind, amount in [("ACTUAL", 5), ("ACTUAL", 9), ("FORECASTED", 10)]
]))
PY
)
aws budgets create-budget --region us-east-1 --account-id "$EXPECTED_ACCOUNT" \
  --budget '{"BudgetName":"FitCorePortfolioPreview","BudgetLimit":{"Amount":"10","Unit":"USD"},"TimeUnit":"MONTHLY","BudgetType":"COST","CostTypes":{"IncludeCredit":false}}' \
  --notifications-with-subscribers "$NOTICES"
aws lightsail create-instances --instance-names "$INSTANCE" --availability-zone "$AZ" \
  --blueprint-id "$BLUEPRINT" --bundle-id "$BUNDLE" --ip-address-type ipv4 --key-pair-name "$KEY_PAIR" \
  --tags key=Project,value=FitCorePortfolio
for attempt in {1..60}; do
  state=$(aws lightsail get-instance-state --instance-name "$INSTANCE" --query state.name --output text)
  if [ "$state" = running ]; then break; fi
  sleep 3
done
test "$state" = running
aws lightsail put-instance-public-ports --instance-name "$INSTANCE" --port-infos \
  "fromPort=22,toPort=22,protocol=tcp,cidrs=$SSH_CIDR" \
  'fromPort=80,toPort=80,protocol=tcp,cidrs=0.0.0.0/0' \
  'fromPort=443,toPort=443,protocol=tcp,cidrs=0.0.0.0/0'
aws lightsail allocate-static-ip --static-ip-name "$STATIC_IP"
aws lightsail attach-static-ip --static-ip-name "$STATIC_IP" --instance-name "$INSTANCE"
IP=$(aws lightsail get-static-ip --static-ip-name "$STATIC_IP" --query staticIp.ipAddress --output text)
printf '%s\n' "$IP"
```

Create an A record for a **new** approved subdomain pointing to `IP`. Confirm the SSH host fingerprint through the Lightsail console before trusting the new host. Upload only the preview archive. [Caddy automatic HTTPS](https://caddyserver.com/docs/automatic-https)

```bash
scp -o StrictHostKeyChecking=yes -i "$SSH_KEY" output/portfolio/fitcore-preview.tgz "ubuntu@$IP:fitcore-preview.tgz"
ssh -o StrictHostKeyChecking=yes -i "$SSH_KEY" "ubuntu@$IP"
```

On the new Ubuntu host, install Docker and Compose from Docker's signed repository. Export the approved DNS name as `SITE_HOST` and the locally recorded archive hash as `PREVIEW_SHA256`. [Docker Engine install on Ubuntu](https://docs.docker.com/engine/install/ubuntu/)

```bash
set -eu
: "${SITE_HOST:?export the approved preview hostname}" "${PREVIEW_SHA256:?export the locally recorded hash}"
[[ "$SITE_HOST" =~ ^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$ && "$SITE_HOST" == *.* ]]
[[ "$PREVIEW_SHA256" =~ ^[a-f0-9]{64}$ ]]
printf '%s  %s\n' "$PREVIEW_SHA256" "$HOME/fitcore-preview.tgz" | sha256sum --check --strict
sudo apt-get update
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
. /etc/os-release
printf 'Types: deb\nURIs: https://download.docker.com/linux/ubuntu\nSuites: %s\nComponents: stable\nArchitectures: %s\nSigned-By: /etc/apt/keyrings/docker.asc\n' \
  "$VERSION_CODENAME" "$(dpkg --print-architecture)" | sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
mkdir "$HOME/fitcore-portfolio"
cd "$HOME/fitcore-portfolio"
tar -xzf "$HOME/fitcore-preview.tgz"
printf 'FITCORE_PREVIEW_ADDRESS=%s\nFITCORE_PREVIEW_HTTP=0.0.0.0:80\nFITCORE_PREVIEW_HTTPS=0.0.0.0:443\n' "$SITE_HOST" > preview.env
sudo docker compose --project-name fitcore-portfolio-preview --env-file preview.env -f compose.preview.yaml config --quiet
sudo docker compose --project-name fitcore-portfolio-preview --env-file preview.env -f compose.preview.yaml up -d
```

After certificate issuance, verify externally with `curl -fsSI "https://$SITE_HOST/"` and `curl -s -o /dev/null -w '%{http_code}\n' "https://$SITE_HOST/api/health"` (expect 404). Check both gym workspaces and review `aws lightsail get-instance-port-states --instance-name "$INSTANCE"`. For a future full stack, use $25 actual and $30 forecast/actual alerts against the approved $30 budget, then measure resource and network limits before inviting reviewers.

**Shutdown.** Stop the preview with `sudo docker compose --project-name fitcore-portfolio-preview --env-file preview.env -f compose.preview.yaml down` on the dedicated host and remove its DNS record. After confirming the account and exact tagged resources, use the approved deletion commands below. A stopped Lightsail VM keeps accruing charges. Verify retained snapshots and any extra disks separately. [Lightsail billing FAQ](https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-frequently-asked-questions-faq-billing-and-account-management.html)

```bash
set -eu
: "${EXPECTED_ACCOUNT:?}" "${INSTANCE:?}" "${STATIC_IP:?}"
test "$(aws sts get-caller-identity --query Account --output text)" = "$EXPECTED_ACCOUNT"
test "$INSTANCE" = fitcore-portfolio-preview
test "$STATIC_IP" = fitcore-portfolio-preview-ip
aws lightsail get-instance --instance-name "$INSTANCE" --query 'instance.{ARN:arn,Tags:tags}'
aws lightsail get-static-ip --static-ip-name "$STATIC_IP" --query 'staticIp.{IP:ipAddress,AttachedTo:attachedTo}'
read -r -p 'Type the approved instance name to confirm deletion: ' confirmed_instance
test "$confirmed_instance" = "$INSTANCE"
aws lightsail detach-static-ip --static-ip-name "$STATIC_IP"
aws lightsail release-static-ip --static-ip-name "$STATIC_IP"
aws lightsail delete-instance --instance-name "$INSTANCE"
aws lightsail get-instance-snapshots --query 'instanceSnapshots[].{Name:name,Source:fromInstanceName}'
aws lightsail get-disks --query 'disks[].{Name:name,AttachedTo:attachedTo}'
```
