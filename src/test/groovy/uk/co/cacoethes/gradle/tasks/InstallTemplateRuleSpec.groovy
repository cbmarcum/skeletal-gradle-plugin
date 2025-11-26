package uk.co.cacoethes.gradle.tasks

import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Zip
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Unroll

class InstallTemplateRuleSpec extends Specification {
    def project
    def rule

    def setup() {
        project = ProjectBuilder.builder().build()
        project.apply plugin: 'net.codebuilders.lazybones-templates'
        rule = new InstallTemplateRule(project)
    }

    def "Rule has correct description"() {
        expect: "The description describes the rule's purpose"
        rule.description == "installTemplate<TmplName> - Installs the named template package into your local cache"
    }

    def "toString returns description with 'Rule:' prefix"() {
        expect:
        rule.toString() == "Rule: ${rule.description}"
    }

    @Unroll
    def "apply creates install task for '#taskName' when package task exists"() {
        given: "A package task exists"
        def packageTask = project.tasks.create("packageTemplate${templateName}", Zip)

        when: "The rule is applied"
        rule.apply(taskName)

        then: "An install task is created"
        def installTask = project.tasks.findByName(taskName)
        installTask != null
        installTask instanceof Copy

        where:
        taskName                      | templateName
        "installTemplateMyTemplate"   | "MyTemplate"
        "installTemplateSimple"       | "Simple"
        "installTemplateFooBar"       | "FooBar"
    }

    def "apply does not create task when package task does not exist"() {
        when: "The rule is applied without a corresponding package task"
        rule.apply("installTemplateNonExistent")

        then: "No install task is created"
        project.tasks.findByName("installTemplateNonExistent") == null
    }

    @Unroll
    def "apply does not create task for invalid task name '#taskName'"() {
        when: "The rule is applied with an invalid task name"
        rule.apply(taskName)

        then: "No task is created"
        project.tasks.findByName(taskName) == null

        where:
        taskName << [
                "installtemplate",
                "installTemplate",
                "installtemplateLowercase",
                "packageTemplateMyTemplate",
                "myCustomTask"
        ]
    }

    def "install task is configured to copy from package task"() {
        given: "A package task exists"
        def packageTask = project.tasks.create("packageTemplateMyTemplate", Zip)
        packageTask.archiveFileName.set("my-template-1.0.zip")
        packageTask.destinationDirectory.set(project.layout.buildDirectory.dir("templates"))

        when: "The rule is applied"
        rule.apply("installTemplateMyTemplate")

        then: "The install task depends on the package task"
        def installTask = project.tasks.findByName("installTemplateMyTemplate") as Copy
        installTask.taskDependencies.getDependencies(installTask).contains(packageTask)
    }

    def "install task is configured with correct destination directory"() {
        given: "A package task and custom install directory"
        def customInstallDir = project.file("custom/install/path")
        project.extensions.lazybones.installDir = customInstallDir
        project.tasks.create("packageTemplateMyTemplate", Zip)

        when: "The rule is applied"
        rule.apply("installTemplateMyTemplate")

        then: "The install task uses the configured install directory"
        def installTask = project.tasks.findByName("installTemplateMyTemplate") as Copy
        installTask.destinationDir == customInstallDir
    }

    def "install task renames package file correctly with suffix"() {
        given: "A package task with a custom suffix"
        project.extensions.lazybones.packageNameSuffix = "-template"
        def packageTask = project.tasks.create("packageTemplateMyTemplate", Zip)

        when: "The rule is applied"
        rule.apply("installTemplateMyTemplate")

        then: "The install task has a rename rule configured"
        def installTask = project.tasks.findByName("installTemplateMyTemplate") as Copy
        installTask.mainSpec.copyActions.any { it.class.simpleName == "RenamingCopyAction" }
    }

    def "package file name transformer renames '#inputName' to '#expectedName'"() {
        given: "A rule with package suffix"
        def transformer = rule.createPackageFileNameTransformer("-template")

        when: "The transformer is applied"
        def result = transformer(inputName)

        then: "The file name is transformed correctly"
        result == expectedName

        where:
        inputName                           | expectedName
        "my-tmpl-template-1.0.zip"          | "my-tmpl-1.0.zip"
        "simple-template-0.1.0.zip"         | "simple-0.1.0.zip"
        "foo-bar-template-2.3.4.zip"        | "foo-bar-2.3.4.zip"
        "test-template-1.0.0-SNAPSHOT.zip"  | "test-1.0.0-SNAPSHOT.zip"
        "no-match.zip"                      | "no-match.zip"
    }

    @Unroll
    def "extractTemplateName extracts '#expected' from '#taskName'"() {
        expect:
        rule.extractTemplateName(taskName) == expected

        where:
        taskName                        | expected
        "installTemplateMyTemplate"     | "MyTemplate"
        "installTemplateSimple"         | "Simple"
        "installTemplateFoo-Bar"        | "Foo-Bar"
        // "installTemplateA"              | "A"
        // "installTemplate123"            | "123"
        "installTemplate"               | null
        "installtemplate"               | null
        "otherTask"                     | null
    }

    def "findPackageTask returns correct package task when it exists"() {
        given: "A package task exists"
        def packageTask = project.tasks.create("packageTemplateMyTemplate", Zip)

        when: "Finding the package task"
        def found = rule.findPackageTask("MyTemplate")

        then: "The correct task is returned"
        found == packageTask
    }

    def "findPackageTask returns null when package task does not exist"() {
        when: "Finding a non-existent package task"
        def found = rule.findPackageTask("NonExistent")

        then: "null is returned"
        found == null
    }

    @Unroll
    def "Rule pattern matches task name '#taskName': #shouldMatch"() {
        given: "A package task for valid cases"
        if (shouldMatch) {
            project.tasks.create("packageTemplate${templateName}", Zip)
        }

        when: "The rule is applied"
        rule.apply(taskName)

        then: "Task is created only if pattern matches"
        (project.tasks.findByName(taskName) != null) == shouldMatch

        where:
        taskName                           | templateName      | shouldMatch
        "installTemplateMyTemplate"        | "MyTemplate"      | true
        "installTemplateSimple"            | "Simple"          | true
        // "installTemplate-my-template"      | "-my-template"    | true
        // "installTemplateA"                 | "A"               | true
        // "installTemplate123Test"           | "123Test"         | true
        "installtemplateLowercase"         | null              | false
        "installTemplate"                  | null              | false
        "packageTemplateTest"              | null              | false
        "randomTask"                       | null              | false
    }
}