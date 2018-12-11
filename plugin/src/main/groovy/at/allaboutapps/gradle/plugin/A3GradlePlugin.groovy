package at.allaboutapps.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger

import java.util.regex.Matcher
import java.util.regex.Pattern

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

            // rename bundle aab file
            def appName = parent.name
            setProperty("archivesBaseName", "${appName} ${getCurrentFlavor()}-vc${versionCode}-${versionName}")

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
            def stdout = new ByteArrayOutputStream()

            Process process = Runtime.getRuntime().exec(args, null, projectDir)
            process.consumeProcessOutputStream(stdout)
            process.waitForOrKill(10 * 1000)

            return stdout.toString()
        } catch (Exception e) {
            logger.warn("Could not run command ${Arrays.toString(args)}", e)
            return ""
        }
    }

    def getCurrentFlavor() {
        def gradle = getGradle()
        String taskReqStr = gradle.getStartParameter().getTaskRequests().toString()
        Pattern pattern
        if (taskReqStr.contains("bundle")) {
            pattern = Pattern.compile("bundle(\\w+)")
        } else {
            pattern = Pattern.compile("assemble(\\w+)")
        }
        Matcher matcher = pattern.matcher(taskReqStr)
        if (matcher.find()) {
            String flavor = matcher.group(1)
            // This makes first character to lowercase.
            char[] c = flavor.toCharArray()
            c[0] = Character.toLowerCase(c[0])
            flavor = new String(c)
            println "getCurrentFlavor:" + flavor
            return flavor
        } else {
            println "getCurrentFlavor:cannot_find_current_flavor"
            return ""
        }
    }
}