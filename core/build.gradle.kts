plugins { kotlin("jvm") }
kotlin { jvmToolchain(21) }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
tasks.register<JavaExec>("liveSmoke") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("cn.traintrip.core.LiveSmokeKt")
}
tasks.register("destinationSmokeClasspath") {
    dependsOn("testClasses")
    doLast { println(sourceSets["test"].runtimeClasspath.asPath) }
}
