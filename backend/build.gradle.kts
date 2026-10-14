// 백엔드 빌드 + 하네스 (03 문서 4·5장)
// `./gradlew check` = spotlessCheck + noDisabledTests + test(+jacoco 리포트)
plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.10.4"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// 의존성 잠금: Trivy fs가 실제 설치 버전을 읽을 수 있도록 gradle.lockfile을 커밋한다.
// STRICT: 잠금 파일이 없거나 build.gradle.kts와 어긋나면 빌드 실패 → "몰래 바뀐 의존성" 방지.
// 의존성을 바꾼 PR에서는 `./gradlew resolveAndLockAll --write-locks`로 잠금 파일을 함께 갱신한다.
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}

// 보안 패치 (2026-10-14, Trivy fs 탐지): Spring Boot 4.1.1이 관리하는 버전에 취약점이 있어 수정 버전으로 올린다.
// - Tomcat 11.0.24 → 11.0.25 : CVE-2026-65182, CVE-2026-65905, CVE-2026-68525 (CRITICAL)
// - Jackson 3.1.5 → 3.1.7    : CVE-2026-89407, -89425, -68497, -91776, -91777 (HIGH)
// Spring Boot를 이 버전 이상을 포함한 패치(4.1.2+)로 올리면 이 두 줄을 지운다. (03 문서 HD-15)
extra["tomcat.version"] = "11.0.25"
extra["jackson-bom.version"] = "3.1.7"
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-mysql")
    runtimeOnly("com.mysql:mysql-connector-j")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// bootJar만 만든다 (plain jar가 섞이면 Dockerfile의 COPY가 모호해짐)
tasks.jar {
    enabled = false
}

tasks.test {
    useJUnitPlatform()
    // 명세 파일(권한 매트릭스·보안 회귀 목록) 점검 결과를 CI 요약에 붙이기 위한 출력 위치
    systemProperty("harness.reportDir", layout.buildDirectory.dir("harness").get().asFile.absolutePath)
    // -Pharness.strict=true 이면 PENDING 항목이 하나라도 남아 있을 때 실패 (03 문서 H-Q3)
    systemProperty("harness.strict", providers.gradleProperty("harness.strict").getOrElse("false"))
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    finalizedBy(tasks.jacocoTestReport)
}

// JaCoCo: 리포트만 생성 (수치 게이트 없음, 02 문서 6.2절)
tasks.jacocoTestReport {
    reports {
        xml.required = true
        html.required = true
    }
}

// 테스트를 끄는 어노테이션 금지: 실패하는 테스트를 "꺼서" 통과시키는 것을 막는다.
val noDisabledTests = tasks.register("noDisabledTests") {
    group = "verification"
    description = "src/test에 테스트 비활성화 어노테이션이 없는지 확인한다."
    val testSources = fileTree("src/test/java") { include("**/*.java") }
    val projectDir = layout.projectDirectory.asFile
    inputs.files(testSources)
    doLast {
        val pattern = Regex("""@(Disabled|DisabledIf\w*|Ignore)\b""")
        val offenders = testSources.files.filter { pattern.containsMatchIn(it.readText()) }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "테스트 비활성화 어노테이션 발견 (03 문서 9장 DoD 위반):\n" +
                    offenders.joinToString("\n") { " - ${it.relativeTo(projectDir)}" },
            )
        }
    }
}

tasks.check {
    dependsOn(noDisabledTests)
}

// 잠금 파일 생성·갱신용 (Gradle 공식 문서 패턴). 반드시 --write-locks와 함께 실행.
tasks.register("resolveAndLockAll") {
    notCompatibleWithConfigurationCache("설정 목록을 실행 시점에 순회한다")
    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks) { "--write-locks 옵션과 함께 실행하세요." }
    }
    doLast {
        configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
    }
}
