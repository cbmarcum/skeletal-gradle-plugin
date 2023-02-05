# Skeletal Gradle Plugin
## Quick Links

* [Applying the Gradle Plugin](https://cbmarcum.github.io/skeletal-gradle-plugin/index.html#_apply_the_plugin)
* [Skeletal Gradle  Plugin User Guide](https://cbmarcum.github.io/skeletal-gradle-plugin/index.html)
* [Skeletal App and Template Guides](https://cbmarcum.github.io/skeletal/index.html)
* [Skeletal Project Creation Tool](https://github.com/cbmarcum/skeletal)

## Introduction

This Gradle plugin is a sibling project to the [Skeletal Project Creation Tool](https://github.com/cbmarcum/skeletal) and was originally a sub-project 
in the original [Lazybones Project](https://github.com/pledbrook/lazybones) 
before this fork of the project. 

The mechanics of publishing Lazybones templates is straightforward and could
be done manually. That doesn't mean it's not a lot of work though. If you want
to manage and publish Lazybones templates, we strongly recommend you use Gradle
along with this plugin.

The plugin allows you to manage multiple templates, giving you the tools to
package, install, and publish them individually or all together. In addition, 
you can also easily set up subtemplates. Let's see how you use the plugin.

## Building the Plugin
To build into a repo within the build output
```shell
./gradlew publish
```
This builds a repo layout that you can use from an Artifactory Gradle repository or similar artifact repository.

checksums and signatures removed for brevity and version may not be latest...
```shell
build/repos/releases
`-- net
    `-- codebuilders
        |-- lazybones-templates
        |   `-- net.codebuilders.lazybones-templates.gradle.plugin
        |       |-- 1.7.1
        |       |   |-- net.codebuilders.lazybones-templates.gradle.plugin-1.7.1.pom
        |       `-- maven-metadata.xml
        `-- skeletal-gradle
            |-- 1.7.1
            |   |-- skeletal-gradle-1.7.1.jar
            |   |-- skeletal-gradle-1.7.1-javadoc.jar
            |   |-- skeletal-gradle-1.7.1.module
            |   |-- skeletal-gradle-1.7.1.pom
            |   |-- skeletal-gradle-1.7.1-sources.jar
            `-- maven-metadata.xml
```

To publish into your local Maven cache
```shell
./gradlew publishToMavenLocal
```
You will need to either setup signing credentials for the [Signing Plugin](https://docs.gradle.org/current/userguide/signing_plugin.html), make the version end in _-SNAPSHOT_, or force the build variable _isReleaseVersion_ to false.

To publish to [Gradle Plugins](https://plugins.gradle.org/plugin/net.codebuilders.lazybones-templates)
```shell
./gradlew publishPlugins
```
This can only be done by the Skeletal project owner for this plugin name.

## Creating a Release
Releases are created in the GitHub repo by creating a tag and a Release based on that tag and a Changelog of major changes and uploading build artifacts and source archives.

This is automated by JReleaser using the jreleaser.yml configuration file.

Steps to create a release after Gradle `publish` or `publishPlugins`
1. `export JRELEASER_PROJECT_VERSION=version to release`
2. `export JRELEASER_OUTPUT_DIRECTORY=build` (until added to jreleaser.yml)
3. `jreleaser config`
4. `jreleaser full-release --dry-run`
5. check `build/jreleaser/release/CHANGELOG.md` for errors
6. `jreleaser full-release`

## Credits

The complete list going back to Lazybones can be found in the [Skeletal Credits](https://github.com/cbmarcum/skeletal#credits).
