# CD 플랜: AWS ECR + EC2(2대+) + ALB + Docker Compose + SSM

버전: 1.0 (팀 합의용 초안)

## 목표

- 이 저장소 정본에 맞춰 **AWS ECR**에 서비스별 이미지를 푸시하고, **ALB 뒤 EC2 2대 이상**에서 **Docker Compose**로 앱 스택을 기동한다.
- **RDS / Amazon MQ(RabbitMQ) / ElastiCache**는 매니지드로 두고, EC2에는 **앱·웹·web-edge 컨테이너만** 올린다.
- 배포는 **GitHub Actions → SSM Run Command**로 EC2에 내려보내고, **인스턴스 단위 롤링**(drain → 배포 → 헬스체크 → 재등록)으로 무중단에 가깝게 만든다.

## 레포 근거

| 항목 | 경로 |
|------|------|
| CI | [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) |
| 경로 필터(재사용) | [`.github/workflows/path-changes.yml`](../.github/workflows/path-changes.yml) |
| Release (ECR) | [`.github/workflows/release.yml`](../.github/workflows/release.yml) |
| Deploy (ALB+SSM) | [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml) |
| 로컬 Compose | [`docker-compose.yml`](../docker-compose.yml) |
| 운영 Compose | [`docker-compose.prod.yml`](../docker-compose.prod.yml) |
| 배포 env 예시 | [`.env.deploy.example`](../.env.deploy.example) |
| IAM·ECR·헬스·롤백 정본 | [`aws-github-oidc-ecr-ssm.md`](aws-github-oidc-ecr-ssm.md) |
| Terraform (OIDC·IAM·ECR·선택 인프라) | [`infra/terraform/README.md`](../infra/terraform/README.md) |
| 아키텍처(패턴 B, K8s 범위 밖) | [`architecture.md`](architecture.md) |
| CD 방향 요약 | [`CI.md`](CI.md) |

## 배포 토폴로지

```mermaid
flowchart LR
  subgraph github [GitHub]
    Repo[Repository]
    Actions[GitHub_Actions]
  end

  subgraph ecr [AWS_ECR]
    ImgRepo[Container_Images]
  end

  subgraph aws [AWS_VPC]
    ALB[ALB_SingleDomain]

    subgraph asg [EC2_AutoScalingGroup]
      EC2A[EC2_Instance_A]
      EC2B[EC2_Instance_B]
    end

    subgraph managed [Managed_Dependencies]
      RDS[RDS_PostgreSQL]
      MQ[Amazon_MQ_RabbitMQ]
      Cache[ElastiCache_Redis]
    end
  end

  Repo --> Actions
  Actions -->|build_and_push| ImgRepo

  ALB -->|HTTP_80_or_443| EC2A
  ALB -->|HTTP_80_or_443| EC2B

  EC2A -->|compose_up| RDS
  EC2A -->|AMQP| MQ
  EC2A -->|TCP| Cache

  EC2B -->|compose_up| RDS
  EC2B -->|AMQP| MQ
  EC2B -->|TCP| Cache

  Actions -->|SSM_RunCommand| EC2A
  Actions -->|SSM_RunCommand| EC2B
```

## GitHub Actions CD 흐름

```mermaid
flowchart TD
  Commit[Push_or_Merge] --> CI[CI_ci.yml]
  CI -->|success| Release[Release_Build_and_Push_to_ECR]
  Release --> DeployStg[Deploy_Staging_via_SSM]
  DeployStg -->|optional_manual_gate| DeployProd[Deploy_Prod_via_SSM]

  DeployStg --> HealthStg[Healthcheck]
  HealthStg -->|fail| RollbackStg[Rollback_previous_sha]
  HealthStg -->|ok| DoneStg[Staging_Ready]

  DeployProd --> HealthProd[Healthcheck]
  HealthProd -->|fail| RollbackProd[Rollback_previous_sha]
  HealthProd -->|ok| DoneProd[Prod_Ready]
```

## 이미지·태그 전략

- **불변 태그(롤백·추적)**: `:<git_sha>`
- **환경 포인터(선택)**: `:staging`, `:prod`
- 배포 시 Compose는 기본적으로 **sha 태그**를 사용해 재현 가능하게 둔다.

## 배포 단위(이미지 목록 — 운영 범위에 맞게 선택)

- 백엔드: `api-gateway-service`, `proxy-service`, `identity-service`, `usage-service`, `billing-service`, `team-service`, `notification-service`
- 웹(Next standalone): `identity-web`, `usage-web`, `billing-web`, `team-web`, `notification-web`, `agent-web`
- 엣지: `web-edge` (단일 도메인 라우팅)

## Release 워크플로 (설계)

- **트리거**: `develop` → 스테이징 자동; `main` → 프로덕션(또는 `workflow_dispatch` + GitHub Environment 승인)
- **빌드·푸시**: `ci.yml`의 `changes`(paths-filter)를 재사용해 **변경된 서비스만** ECR에 push
- **도구**: `docker/build-push-action` + `push: true`
- **AWS 인증**: GitHub **OIDC AssumeRole** (Access Key 지양)

## Deploy 워크플로 — SSM 롤링 (설계)

대상: ALB Target Group에 등록된 EC2(ASG 권장).

인스턴스별 순서:

1. ALB에서 해당 인스턴스 **drain**(연결 종료 후 등록 해제)
2. SSM Run Command로 배포 스크립트 실행:
   - ECR 로그인
   - `docker compose -f <prod_compose> pull`
   - `docker compose -f <prod_compose> up -d`
3. 로컬/경량 **헬스체크**(예: `web-edge` 또는 게이트웨이 경로)
4. 성공 시 Target Group **재등록**
5. 실패 시 **이전 `git_sha` 이미지 태그**로 롤백 후 재검증

## 운영용 Compose (설계)

- 로컬 [`docker-compose.yml`](../docker-compose.yml)의 Postgres/RabbitMQ/Redis **컨테이너 정의는 운영 파일에서 제외**한다.
- 별도 파일 예: `docker-compose.prod.yml` (저장소에 둘지, 배포 시 생성할지 팀 합의)
- 앱 `environment`는 **RDS / Amazon MQ / ElastiCache 엔드포인트**와 자격증명(또는 IAM 가능 영역)을 가리키게 한다.
- 비밀값: **SSM Parameter Store** 또는 **Secrets Manager**에서 주입(서버 로컬 `.env`만 쓸 경우 권한·로테이션 정책 필수)

## 헬스체크·롤백

- ALB Target Group 헬스 경로: `web-edge`의 안정 경로(또는 게이트웨이 헬스)로 통일
- 배포 스크립트 내: N회 재시도 후 실패 시 롤백
- 롤백 기준: 배포 직전 **성공했던 `git_sha`**를 인스턴스 또는 SSM 파라미터에 보관

## Terraform 적용 순서 (IaC)

1. **[`infra/terraform/bootstrap/README.md`](../infra/terraform/bootstrap/README.md)** 로 S3 상태 버킷·DynamoDB 락 테이블 생성 (로컬 state).
2. **[`infra/terraform/README.md`](../infra/terraform/README.md)** 에서 backend 설정 후 `terraform init` / `apply` 로 OIDC 프로바이더, 환경별 Release·Deploy 역할, ECR 리포지토리 생성.
3. 선택: 동일 루트에서 `enable_compute_stack = true` 로 VPC·ALB·Target Group·ASG·인스턴스 프로파일 추가 (단순 퍼블릭 레이아웃).
4. Terraform 출력값을 GitHub Environment 변수에 반영 (표는 Terraform README).

워크플로([`release.yml`](../.github/workflows/release.yml), [`deploy.yml`](../.github/workflows/deploy.yml))와 Compose·SSM 스크립트는 그대로 두고, ARN·리포 이름만 출력과 맞춘다.

## AWS 리소스 체크리스트

- ECR 리포지토리(이미지별 또는 통합 네이밍) — Terraform [`modules/ecr`](../infra/terraform/modules/ecr) 또는 기존 리소스
- IAM: GitHub OIDC Role(ECR push + SSM `SendCommand` 등) — Terraform [`modules/github_env_roles`](../infra/terraform/modules/github_env_roles) 또는 수동 JSON([`aws-github-oidc-ecr-ssm.md`](aws-github-oidc-ecr-ssm.md))
- EC2: Instance Profile(ECR pull + SSM Agent), ASG 최소 2대, Launch Template에 Docker/Compose — Terraform [`modules/compute_stack`](../infra/terraform/modules/compute_stack) 선택 또는 수동
- ALB + Target Group + 보안 그룹 — 선택 모듈 또는 수동; 배포 스크립트 [`gha-roll-instance.sh`](../scripts/deploy/gha-roll-instance.sh) 의 `TARGET_PORT`(기본 80)와 TG 포트 일치
- EC2 → RDS / MQ / Redis 네트워크 허용

## 주의사항 (이 프로젝트 특성)

- `GATEWAY_SHARED_SECRET` 등은 게이트웨이·프록시·usage 등 **동일 값** 유지 — [`contracts/gateway-proxy.md`](contracts/gateway-proxy.md)
- 서비스별 DB 분리 원칙은 RDS 구성에도 반영 — [`msa-database-and-service-integration.md`](msa-database-and-service-integration.md)
- Compose는 **컨테이너 단위 세밀 롤링**보다 **인스턴스 단위 롤링**이 현실적

## 구현 TODO (체크리스트)

- [ ] 스테이징/프로덕션 환경 수와 브랜치 트리거 확정 (`develop` / `main`)
- [ ] 운영에 포함할 이미지 목록·ECR 리포지토리 네이밍 확정
- [ ] GitHub OIDC IAM Role 및 최소 권한 정책 — Terraform [`infra/terraform`](../infra/terraform/README.md) 또는 수동 JSON
- [ ] `docker-compose.prod.yml`(또는 동등) 초안 및 env/secret 주입 방식 확정
- [ ] Release 워크플로 추가(변경 감지, ECR push, sha 태그)
- [ ] Deploy 워크플로 추가(SSM, drain/roll, 헬스체크, 롤백)
- [ ] ALB Target Group 헬스 경로·타임아웃·재시도 정책 확정
- [ ] (선택) Terraform 원격 상태 부트스트랩 및 `enable_compute_stack` 로 ALB/ASG 프로비저닝

---

문서 유지: CD 구현이 바뀌면 본 파일과 [`CI.md`](CI.md) · [`architecture.md`](architecture.md) §10과 모순이 없는지 확인한다.
