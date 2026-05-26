// =============================================================================
//  Rooti-Server build script
//  - Spring Boot 3.3.x / Java 21
//  - Package-by-feature, Hexagonal-lite layering
// =============================================================================
plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"

    // Code quality
    id("com.diffplug.spotless") version "6.25.0"

    // Sonar / coverage (선택적으로 사용)
    jacoco
}

group = "com.rooti"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

// QueryDSL 생성 디렉토리
val querydslDir = layout.buildDirectory.dir("generated/querydsl")

sourceSets {
    main {
        java {
            srcDir(querydslDir)
        }
    }
}

dependencies {
    // ---------- Spring Boot Core ----------
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // ---------- Persistence ----------
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // QueryDSL (Jakarta)
    implementation("com.querydsl:querydsl-jpa:5.1.0:jakarta")
    annotationProcessor("com.querydsl:querydsl-apt:5.1.0:jakarta")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")

    // ---------- Security & JWT ----------
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // ---------- API Documentation ----------
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    // ---------- Mapping & Utility ----------
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    // ---------- Firebase Push ----------
    implementation("com.google.firebase:firebase-admin:9.4.1")

    // ---------- PDF generation (WeasyPrint 대체) ----------
    // OpenHTMLtoPDF: HTML(+CSS) → PDF (Java-native, 라이센스 free)
    implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10")
    implementation("com.openhtmltopdf:openhtmltopdf-svg-support:1.0.10")
    implementation("org.jsoup:jsoup:1.18.1")

    // Apache POI: 근무일지 XLSX 렌더링용 (PDF/HWP 와 동일 데이터를 다른 그릇에 담음)
    implementation("org.apache.poi:poi-ooxml:5.3.0")

    // ---------- Misc ----------
    implementation("com.github.f4b6a3:ulid-creator:5.2.3")     // ULID id (DB friendly)
    implementation("commons-io:commons-io:2.17.0")
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("com.googlecode.libphonenumber:libphonenumber:8.13.50")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // ---------- Logging (구조화) ----------
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    // ---------- Dev tools ----------
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // ---------- Test ----------
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.mockito", module = "mockito-core")
    }
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testImplementation("org.assertj:assertj-core:3.26.3")

    // TestContainers
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.testcontainers:postgresql:1.20.4")

    // RestAssured for E2E
    testImplementation("io.rest-assured:rest-assured:5.5.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(
        listOf(
            "-parameters",
            "-Xlint:unchecked",
            "-Xlint:deprecation"
        )
    )
    // QueryDSL Q-class generation directory
    options.generatedSourceOutputDirectory.set(querydslDir.get().asFile)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
    systemProperty("user.timezone", "Asia/Seoul")
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        html.required.set(true)
        xml.required.set(true)
    }
}

tasks.clean {
    delete(querydslDir)
}

// Spotless: 자동 코드 포맷팅
spotless {
    java {
        target("src/**/*.java")
        targetExclude("$buildDir/generated/**/*.java")
        googleJavaFormat("1.22.0").aosp().reflowLongStrings()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.3.1")
    }
}

springBoot {
    buildInfo() // /actuator/info 에서 빌드 정보 노출
}
