## Build log (run 33744180884)


Welcome to Gradle 8.5!

Here are the highlights of this release:
 - Support for running on Java 21
 - Faster first use with Kotlin DSL
 - Improved error and warning messages

For more details see https://docs.gradle.org/8.5/release-notes.html

To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/8.5/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon will be stopped at the end of the build 
> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:mergeDebugNativeDebugMetadata NO-SOURCE
> Task :app:checkKotlinGradlePluginConfigurationErrors
> Task :app:dataBindingMergeDependencyArtifactsDebug
> Task :app:generateDebugResValues
> Task :app:generateDebugResources
> Task :app:packageDebugResources
> Task :app:mergeDebugResources
> Task :app:parseDebugLocalResources
> Task :app:checkDebugAarMetadata
> Task :app:dataBindingGenBaseClassesDebug
> Task :app:mapDebugSourceSetPaths
> Task :app:createDebugCompatibleScreenManifests
> Task :app:extractDeepLinksDebug
> Task :app:processDebugMainManifest
> Task :app:processDebugManifest
> Task :app:javaPreCompileDebug
> Task :app:mergeDebugShaders
> Task :app:compileDebugShaders NO-SOURCE
> Task :app:generateDebugAssets UP-TO-DATE
> Task :app:mergeDebugAssets
> Task :app:processDebugManifestForPackage
> Task :app:compressDebugAssets
> Task :app:processDebugResources
> Task :app:desugarDebugFileDependencies
> Task :app:mergeDebugJniLibFolders
> Task :app:checkDebugDuplicateClasses
> Task :app:mergeDebugNativeLibs

> Task :app:compileDebugKotlin
e: file:///home/runner/work/Port-SIP/Port-SIP/app/src/main/java/com/chatapp/modern/ui/settings/SettingsActivity.kt:41:37 Unresolved reference: useWebRtc
e: file:///home/runner/work/Port-SIP/Port-SIP/app/src/main/java/com/chatapp/modern/ui/settings/SettingsActivity.kt:62:17 Unresolved reference: useWebRtc
e: file:///home/runner/work/Port-SIP/Port-SIP/app/src/main/java/com/chatapp/modern/ui/settings/SettingsActivity.kt:62:27 Variable expected
e: file:///home/runner/work/Port-SIP/Port-SIP/app/src/main/java/com/chatapp/modern/webrtc/WebRtcCallEngine.kt:160:17 Type mismatch: inferred type is Call? but Call was expected
e: file:///home/runner/work/Port-SIP/Port-SIP/app/src/main/java/com/chatapp/modern/webrtc/WebRtcCallEngine.kt:166:17 Type mismatch: inferred type is Call? but Call was expected
e: file:///home/runner/work/Port-SIP/Port-SIP/app/src/main/java/com/chatapp/modern/webrtc/WebRtcCallEngine.kt:194:57 Unresolved reference: SENDRECV
e: file:///home/runner/work/Port-SIP/Port-SIP/app/src/main/java/com/chatapp/modern/webrtc/WebRtcCallEngine.kt:205:29 Function invocation 'signalingState()' expected
e: file:///home/runner/work/Port-SIP/Port-SIP/app/src/main/java/com/chatapp/modern/webrtc/WebRtcCallEngine.kt:322:80 Unresolved reference: GATHER_CONTINUOUSLY
e: file:///home/runner/work/Port-SIP/Port-SIP/app/src/main/java/com/chatapp/modern/webrtc/WebRtcCallEngine.kt:386:66 Type mismatch: inferred type is Call? but Call was expected
e: file:///home/runner/work/Port-SIP/Port-SIP/app/src/main/java/com/chatapp/modern/webrtc/WebRtcCallEngine.kt:400:17 Type mismatch: inferred type is Call? but Call was expected
e: file:///home/runner/work/Port-SIP/Port-SIP/app/src/main/java/com/chatapp/modern/webrtc/WebRtcCallEngine.kt:436:33 Type mismatch: inferred type is Call? but Call was expected
e: file:///home/runner/work/Port-SIP/Port-SIP/app/src/main/java/com/chatapp/modern/webrtc/WebRtcCallEngine.kt:449:16 Function invocation 'signalingState()' expected
e: file:///home/runner/work/Port-SIP/Port-SIP/app/src/main/java/com/chatapp/modern/webrtc/WebRtcCallEngine.kt:486:25 Type mismatch: inferred type is Call? but Call was expected
e: file:///home/runner/work/Port-SIP/Port-SIP/app/src/main/java/com/chatapp/modern/webrtc/WebRtcCallEngine.kt:495:71 Type mismatch: inferred type is Call? but Call was expected
e: file:///home/runner/work/Port-SIP/Port-SIP/app/src/main/java/com/chatapp/modern/webrtc/WebRtcCallEngine.kt:586:21 Type mismatch: inferred type is Call? but Call was expected

> Task :app:compileDebugKotlin FAILED
> Task :app:mergeExtDexDebug

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':app:compileDebugKotlin'.
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
   > Compilation error. See log for more details

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.

BUILD FAILED in 1m 32s
25 actionable tasks: 25 executed
