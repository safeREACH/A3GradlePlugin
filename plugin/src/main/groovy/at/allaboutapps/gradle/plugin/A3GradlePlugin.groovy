package at.allaboutapps.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.Copy

class A3GradlePlugin implements Plugin<Project> {

    void apply(Project target) {

        def unsigned = false

        def gitVersionCode = extractVersionCode(target.logger, target.projectDir)
        def gitVersionName = extractVersionName(target.logger, target.projectDir)

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
                // only for releases
                if (!variant.buildType.debuggable) {
                    variant.outputs.all { output ->
                        def splitIdentifier = output.getFilters()?.collect { it.getIdentifier() }?.join("-") ?: ""
                        def split = splitIdentifier.isEmpty() ? splitIdentifier : "-${splitIdentifier.toLowerCase()}"
                        outputFileName = "${target.name}-${variant.name}$split-vc${output.versionCode}-${variant.versionName}.apk"
                    }
                }
            }

            lintOptions {
                abortOnError false
            }
        }

        // rename bundles by copying them to a /outputs/renamedBundle directory
        target.tasks.whenTaskAdded { task ->
            if (task.name.startsWith("bundle")) {
                def renameTaskName = "rename${task.name.capitalize()}Aab"
                def flavor = task.name.substring("bundle".length()).uncapitalize()
                target.tasks.create(renameTaskName, Copy) {
                    from("${target.buildDir}/outputs/bundle/${flavor}/")
                    include "app.aab"
                    destinationDir target.file("${target.buildDir}/outputs/renamedBundle/")
                    rename "app.aab", "${project.name}-${flavor}-vc${gitVersionCode}-${gitVersionName}.aab"
                }

                task.finalizedBy(renameTaskName)
            }
        }

    }

    static String extractVersionName(Logger logger, File projectDir) {
        def result = executeCommand(["git", "describe", "--tags", "--always"] as String[], projectDir, logger)
        try {
            return result.toString().trim()
        } catch (Exception e) {
            logger.warn("Could not read version name from `$result`, defaulting to `1.0.0 (?)`")
            return "1.0.0 (?)"
        }
    }

    static int extractVersionCode(Logger logger, File projectDir) {
        def result = executeCommand(["git", "rev-list", "--count", "HEAD"] as String[], projectDir, logger)
        try {
            result.trim().toInteger()
        } catch (Exception e) {
            logger.warn("Could not read version code from `$result`, defaulting to `1`")
            return 1
        }
    }

    static String executeCommand(String[] args, File projectDir, Logger logger) {
        try {
            def output = new StringBuilder()

            Process process = Runtime.getRuntime().exec(args, null, projectDir)

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()))
            BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()))

            String s
            while ((s = stdInput.readLine()) != null) {
                output.append(s)
            }

            // read any errors from the attempted command
            while ((s = stdError.readLine()) != null) {
                logger.error(s)
            }

            return output.toString()
        } catch (Exception e) {
            logger.warn("Could not run command ${Arrays.toString(args)}", e)
            return ""
        }
    }
}