# 브랜치 네이밍 · Git Flow · 브랜치 보호 규칙

팀이 **브랜치를 만들고·병합하고·보호하는 방식**의 정본이다. (커밋 메시지 형식은 `docs/commit-conventions.md`를 따른다.)

---

## 1. 브랜치 이름과 역할

| 브랜치 이름 | 성격 | 설명 |
|-------------|------|------|
| `main` | 배포용 | 실제 서비스에 가깝게 유지하는 **가장 안정적인** 코드. **직접 수정 금지.** 배포 가능한 상태를 유지하는 주 브랜치. |
| `develop` | 개발용 | 다음 버전 출시를 위해 기능을 모으는 **중심** 브랜치. 기능 추가·버그 수정이 모여 배포 가능한 수준이 되면 `develop` → `main`으로 병합할 수 있다. |
| `feature/기능명` | 기능 개발 | 새 기능 개발 시 사용. **`develop`에서 분기**하고, 완료 후 **`develop`으로 병합**. 사용이 끝난 `feature` 브랜치는 삭제 권장. 로컬 위주 작업 가능. 예: `feature/proxy-auth`, `feature/login-page`, `feat/api-integration` |
| `fix/버그명` | 버그 수정 | 일반 버그 수정. 예: `fix/crash-on-startup` |
| `hotfix/버그명` | 긴급 수정 | **배포된 최종 버전**에서 긴급히 고칠 때 사용. **`main`에서 분기**해 필요한 부분만 수정 후 **`main`에 병합**. 예: `hotfix/security-patch` |
| `{이름|닉네임}/작업명` 또는 `wip/…` | 개인·실험 | 개인 테스트·실험용. `wip`는 work in progress 의미. 예: `yena/fix-proxy`, `wip/fix-proxy` |

### 1.1 이슈 기반 브랜치

- 이슈와 연동할 때는 다음 형식을 쓴다.  
  `issue-{이슈번호}-{브랜치용-이름}`  
  예: `feature/issue-13-login` (프로젝트에서 접두사 `feature/` 등과 조합해 쓸 수 있다.)

---

## 2. 네이밍 규칙 (공통)

- **소문자**를 사용한다.
- 단어 구분은 **하이픈(`-`)** 또는 **슬래시(`/`)** 를 사용한다.  
  예: `feature/login-page` ✅ / `feature_login_page` ❌
- 위 표에 맞는 **접두사**(`feature/`, `fix/`, `hotfix/` 등)를 일관되게 쓴다.

---

## 3. Git 브랜치 전략 (Git Flow)

### 3.1 절대 준수

- **`main`에서 직접 기능 작업을 하지 않는다.**

### 3.2 권장 개발 흐름(요약)

1. 원격 `develop` 최신화: 예) `git fetch upstream` 후 `develop` 갱신  
2. `develop` 체크아웃  
3. `git checkout -b feature/기능명` 등으로 작업 브랜치 생성  
4. 기능 개발 및 커밋 (`docs/commit-conventions.md` 준수)  
5. 원격에 푸시: 예) `git push origin feature/기능명`  
6. **Pull Request**: `feature/기능명` → **upstream `develop`** (검토 후 병합)  
7. 검증 후 **`develop` → `main`** 은 릴리스·합의 절차에 따라 병합  
8. 병합 후 fork 쪽 `feature` 브랜치 삭제(선택)

(저장소 이름 `upstream` / `origin`은 팀 포크·원격 설정에 맞게 조정한다.)

---

## 4. 브랜치 보호 규칙 (Branch Protection, 요약)

GitHub 등에서 설정하는 값의 **팀 합의 요약**이다. 실제 스위치는 저장소 설정을 따른다.

### 4.1 `main`

- 병합 전 **Pull Request 필수**
- (가능 시) **CI/상태 검사 통과** 후 병합 — GitHub Actions 필수 체크 예시는 `docs/CI.md` 참고(잡 이름 **`CI summary`**).
- 위 설정 **우회 불가** (관리자 포함)

### 4.2 `develop`

- 병합 전 **Pull Request 필수**
- **승인 2명** 필요(팀 설정값)
- **CI/상태 검사 통과** 후 병합(`docs/CI.md`, **`CI summary`** 잡)
- 리뷰 **대화(스레드) 해결** 후 병합
- 위 설정 **우회 불가** (관리자 포함)

---

## 5. 문서 유지

- 기본 브랜치·보호 규칙·플로우가 바뀌면 본 문서를 먼저 수정한다.
