dependencies {
    implementation(projects.shared)
    implementation(libs.bstats.velocity)
    compileOnly(libs.velocity)
    compileOnly("org.projectlombok:lombok:1.18.20")
    annotationProcessor(libs.velocity)
    annotationProcessor("org.projectlombok:lombok:1.18.20")
}