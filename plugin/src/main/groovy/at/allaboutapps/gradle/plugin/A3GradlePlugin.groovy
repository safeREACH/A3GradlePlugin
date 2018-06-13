package at.allaboutapps.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class A3GradlePlugin implements Plugin<Project> {

    void apply(Project target) {

        def unsigned = false

        def gitVersionCode = extractVersionCode(target.projectDir)
        def gitVersionName = extractVersionName(target.projectDir)

        target.android.defaultConfig {
            versionCode gitVersionCode
            versionName gitVersionName
        }

        target.android {

            // Add `INTERNAL` as alternative to `DEBUG`, since debug is also true for Preview builds
            defaultConfig.buildConfigField "boolean", "INTERNAL", "Boolean.valueOf(\"false\")"

            signingConfigs {
                aaaDebugKey {
                    if (!target.hasProperty("keyStore")) {
                        target.logger.warn "'keyStore' property not found. Please add the path to the aaa keystore as 'keyStore' to your global gradle.properties file."
                    } else {
                        File keyStoreFile = target.file("$target.keyStore")
                        storeFile keyStoreFile
                        storePassword "android"
                        keyAlias "androiddebugkey"
                        keyPassword "android"
                    }
                }
                releaseKey {
                    if (target.hasProperty("releaseKeyStore_file")) {
                        File keyStoreFile = target.file("$target.releaseKeyStore_file")
                        storeFile = keyStoreFile
                        storePassword target['releaseKeyStore_password']
                        keyAlias target['releaseKeyStore_alias']
                        keyPassword target['releaseKeyStore_keyPassword']
                    } else {
                        target.logger.info "No release keystore defined in 'releaseKeyStore', initializing unsigned build"
                        storePassword ""
                        keyAlias ""
                        keyPassword ""
                        unsigned = true
                    }
                }
            }

            buildTypes {
                debug {
                    buildConfigField "boolean", "INTERNAL", "Boolean.valueOf(\"true\")"

                    minifyEnabled false
                    signingConfig signingConfigs.aaaDebugKey
                }

                release {
                    minifyEnabled false
                    if (unsigned) {
                        signingConfig null
                    } else {
                        signingConfig signingConfigs.releaseKey
                    }

                }

                preview.initWith(buildTypes.release)
                preview {
                    debuggable true
                    matchingFallbacks = ['release']
                }
            }

            // add support for a `neverBuildRelease` flag on flavors.
            // Use as `productFlavors.development.ext.neverBuildRelease = true`
            variantFilter { variant ->
                def buildType = variant.buildType.name
                if (buildType == "release") {
                    variant.flavors.each { flavor ->
                        if (flavor.ext.properties.get("neverBuildRelease", false)) {
                            variant.ignore = true
                        }
                    }
                }
            }

            packagingOptions {
                exclude 'LICENSE.txt'
                exclude 'META-INF/LICENSE.txt'
                exclude 'META-INF/NOTICE.txt'
                exclude 'META-INF/ASL2.0'
                exclude 'META-INF/DEPENDENCIES'
                exclude 'META-INF/LICENSE'
                exclude 'META-INF/NOTICE'
            }

            // rename apk file
            applicationVariants.all { variant ->
                variant.outputs.all { output ->
                    def splitIdentifier = output.getFilters()?.collect { it.getIdentifier() }?.join("-") ?: ""
                    def split = splitIdentifier.isEmpty() ? splitIdentifier : "-${splitIdentifier.toLowerCase()}"
                    outputFileName = "${project.name}-${variant.name}$split-vc${output.versionCode}-${variant.versionName}.apk"
                }
            }

            lintOptions {
                abortOnError false
            }
        }
    }

    static String extractVersionName(File projectDir) {
        def stdout = new ByteArrayOutputStream()
        String[] args = ["git", "describe", "--tags", "--always"]
        Process process = Runtime.getRuntime().exec(args, null, projectDir)
        process.consumeProcessOutputStream(stdout)
        process.waitForOrKill(10 * 1000)

        return stdout.toString().trim()
    }

    static int extractVersionCode(File projectDir) {
        def stdout = new ByteArrayOutputStream()
        String[] args = ["git", "rev-list", "--count", "HEAD"]

        Process process = Runtime.getRuntime().exec(args, null, projectDir)
        process.consumeProcessOutputStream(stdout)
        process.waitForOrKill(10 * 1000)

        return stdout.toString().trim().toInteger()
    }
}