tasks.register("test") {
    dependsOn(gradle.includedBuild("e2immu-external-support").task(":test"))
//    dependsOn(gradle.includedBuild("e2immu-internal-graph").task(":test"))
    dependsOn(gradle.includedBuild("e2immu-cst-impl").task(":test"))
    dependsOn(gradle.includedBuild("e2immu-cst-io").task(":test"))
    dependsOn(gradle.includedBuild("e2immu-cst-print").task(":test"))
    dependsOn(gradle.includedBuild("e2immu-java-parser").task(":test"))
    dependsOn(gradle.includedBuild("e2immu-java-bytecode").task(":test"))
    dependsOn(gradle.includedBuild("e2immu-inspection-integration").task(":test"))
    dependsOn(gradle.includedBuild("e2immu-shallow-analyzer").task(":test"))
}
tasks.register("clean") {
    dependsOn(gradle.includedBuilds.map { it.task(":clean") })
}
