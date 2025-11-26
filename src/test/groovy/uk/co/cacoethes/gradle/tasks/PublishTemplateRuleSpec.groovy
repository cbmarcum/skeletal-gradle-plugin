package uk.co.cacoethes.gradle.tasks

import org.gradle.api.GradleException
import org.gradle.api.tasks.bundling.Zip
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Unroll
import uk.co.cacoethes.gradle.lazybones.TemplateConvention

class PublishTemplateRuleSpec extends Specification {
    def project
    def rule
    def packageTask

    def setup() {
        project = ProjectBuilder.builder().build()
        project.apply plugin: 'net.codebuilders.lazybones-templates'
        rule = new PublishTemplateRule(project)

        // Create a mock package task

        /*packageTask = project.tasks.create("packageTemplateMyTemplate", Zip) {
            archiveBaseName.set("my-template-template")
            archiveVersion.set("1.0.0")
            destinationDirectory.set(project.buildDir)
        }*/
    }

    def "Rule has correct description"() {
        expect: "The description describes the rule's purpose"
        rule.description == "publishTemplate<TmplName> - Publishes the named template package for a URL repository"
    }

    def "toString returns description with 'Rule:' prefix"() {
        expect:
        rule.toString() == "Rule: ${rule.description}"
    }

    @Unroll
    def "apply creates publish task for '#taskName' when package task exists"() {
        given: "A template directory exists"
        def templateDir = project.file("src/templates/${templateName}")
        templateDir.mkdirs()
        new File(templateDir, "DESCRIPTION").text = "A template description"
        project.extensions.lazybones.templateDirs.setFrom([templateDir])

        and: "A package task exists"
        def pkgTask = project.tasks.create("packageTemplate${packageTaskSuffix}", Zip) {
            archiveBaseName.set(templateName)
            archiveVersion.set("1.0.0")
            destinationDirectory.set(project.buildDir)
        }

        and: "TemplateOwner is configured"
        project.extensions.lazybones.templateOwner = "Test Owner"

        when: "The rule is applied"
        rule.apply(taskName)

        then: "A publish task is created"
        def publishTask = project.tasks.findByName(taskName)
        publishTask != null
        publishTask instanceof SimpleManifestEntry

        where:
        taskName                 | packageTaskSuffix | templateName
        "publishTemplateMyTemplate" | "MyTemplate"     | "my-template"
        "publishTemplateSimple"     | "Simple"         | "simple"
        "publishTemplateFooBar"     | "FooBar"         | "foo-bar"
    }

    def "apply does not create task when package task does not exist"() {
        when: "The rule is applied without a corresponding package task"
        rule.apply("publishTemplateNonExistent")

        then: "No publish task is created"
        project.tasks.findByName("publishTemplateNonExistent") == null
    }

    @Unroll
    def "apply does not create task for invalid task name '#taskName'"() {
        when: "The rule is applied with an invalid task name"
        rule.apply(taskName)

        then: "No task is created"
        project.tasks.findByName(taskName) == null

        where:
        taskName << [
                "publishtemplate",
                "publishTemplate",
                "publishTemplateLowercase",
                "packageTemplateMyTemplate",
                "myCustomTask"
        ]
    }

    def "publish task depends on package task"() {
        given: "A template directory and package task"
        def templateDir = project.file("src/templates/my-template")
        templateDir.mkdirs()
        new File(templateDir, "DESCRIPTION").text = "Test template"
        project.extensions.lazybones.templateDirs.setFrom([templateDir])
        project.extensions.lazybones.templateOwner = "Test Owner"

        and: "A package task exists"
        def pkgTask = project.tasks.create("packageTemplateMyTemplate", Zip) {
            archiveBaseName.set("my-template")
            archiveVersion.set("1.0.0")
            destinationDirectory.set(project.buildDir)
        }

        when: "The rule is applied"
        rule.apply("publishTemplateMyTemplate")

        then: "The publish task depends on the package task"
        def publishTask = project.tasks.findByName("publishTemplateMyTemplate")
        publishTask.dependsOn.contains(pkgTask)
    }

    def "publish task uses template description from DESCRIPTION file"() {
        given: "A template directory with DESCRIPTION file"
        def templateDir = project.file("src/templates/my-template")
        templateDir.mkdirs()
        new File(templateDir, "DESCRIPTION").text = "My awesome template"
        project.extensions.lazybones.templateDirs.setFrom([templateDir])
        project.extensions.lazybones.templateOwner = "Test Owner"

        and: "A package task exists"
        project.tasks.create("packageTemplateMyTemplate", Zip) {
            archiveBaseName.set("my-template")
            archiveVersion.set("1.0.0")
            destinationDirectory.set(project.buildDir)
        }

        when: "The rule is applied"
        rule.apply("publishTemplateMyTemplate")

        then: "The publish task has the correct template description"
        def publishTask = project.tasks.findByName("publishTemplateMyTemplate") as SimpleManifestEntry
        publishTask.tmplDescription == "My awesome template"
    }

    def "publish task uses default description when DESCRIPTION file is missing"() {
        given: "A template directory without DESCRIPTION file"
        def templateDir = project.file("src/templates/my-template")
        templateDir.mkdirs()
        project.extensions.lazybones.templateDirs.setFrom([templateDir])
        project.extensions.lazybones.templateOwner = "Test Owner"

        and: "A package task exists"
        project.tasks.create("packageTemplateMyTemplate", Zip) {
            archiveBaseName.set("my-template")
            archiveVersion.set("1.0.0")
            destinationDirectory.set(project.buildDir)
        }

        when: "The rule is applied"
        rule.apply("publishTemplateMyTemplate")

        then: "The publish task has the default description"
        def publishTask = project.tasks.findByName("publishTemplateMyTemplate") as SimpleManifestEntry
        publishTask.tmplDescription == "Missing DESCRIPTION file"
    }

    def "publish task is configured with correct properties"() {
        given: "A template directory with DESCRIPTION file"
        def templateDir = project.file("src/templates/my-template")
        templateDir.mkdirs()
        new File(templateDir, "DESCRIPTION").text = "Test template description"
        project.extensions.lazybones.templateDirs.setFrom([templateDir])

        and: "LazyBones extension is configured"
        project.extensions.lazybones.templateOwner = "Template Owner"

        and: "A package task exists"
        def pkgTask = project.tasks.create("packageTemplateMyTemplate", Zip) {
            archiveBaseName.set("my-template-pkg")
            archiveVersion.set("2.0.0")
            destinationDirectory.set(project.buildDir)
        }

        when: "The rule is applied"
        rule.apply("publishTemplateMyTemplate")

        then: "The publish task has correct configuration"
        def publishTask = project.tasks.findByName("publishTemplateMyTemplate") as SimpleManifestEntry
        publishTask.packageName == "my-template-pkg"
        publishTask.version == "2.0.0"
        publishTask.tmplOwner == "Template Owner"
        publishTask.tmplDescription == "Test template description"
    }

    def "throws exception when template owner is not provided"() {
        given: "A template directory"
        def templateDir = project.file("src/templates/my-template")
        templateDir.mkdirs()
        new File(templateDir, "DESCRIPTION").text = "Test"
        project.extensions.lazybones.templateDirs.setFrom([templateDir])

        and: "No template owner configured"
        project.extensions.lazybones.templateOwner = null

        and: "A package task exists"
        project.tasks.create("packageTemplateMyTemplate", Zip) {
            archiveBaseName.set("my-template")
            archiveVersion.set("1.0.0")
            destinationDirectory.set(project.buildDir)
        }

        when: "The publish task is executed"
        def publishTask = project.tasks.findByName("publishTemplateMyTemplate")
        rule.apply("publishTemplateMyTemplate")
        publishTask.actions[0].execute(publishTask)

        then: "A GradleException is thrown"
        thrown(GradleException)
    }

    def "throws exception when template owner is empty string"() {
        given: "A template directory"
        def templateDir = project.file("src/templates/my-template")
        templateDir.mkdirs()
        new File(templateDir, "DESCRIPTION").text = "Test"
        project.extensions.lazybones.templateDirs.setFrom([templateDir])

        and: "Template owner is empty"
        project.extensions.lazybones.templateOwner = ""

        and: "A package task exists"
        project.tasks.create("packageTemplateMyTemplate", Zip) {
            archiveBaseName.set("my-template")
            archiveVersion.set("1.0.0")
            destinationDirectory.set(project.buildDir)
        }

        when: "The publish task is executed"
        rule.apply("publishTemplateMyTemplate")
        def publishTask = project.tasks.findByName("publishTemplateMyTemplate")
        publishTask.actions[0].execute(publishTask)

        then: "A GradleException is thrown"
        thrown(GradleException)
    }

    @Unroll
    def "taskToTemplateName converts '#taskName' to '#expected'"() {
        expect:
        rule.taskToTemplateName(taskName) == expected

        where:
        taskName           | expected
        "MyTemplate"       | "my-template"
        "Simple"           | "simple"
        "FooBar"           | "foo-bar"
        "-my-template"     | "my-template"
        "-some-template"   | "some-template"
    }

    def "verifyPublishProperties returns empty list when tmplOwner is set"() {
        given: "A mock task with tmplOwner property"
        SimpleManifestEntry mockTask = Stub()
        mockTask.tmplOwner >> "Test Owner"

        expect:
        rule.verifyPublishProperties(mockTask).isEmpty()
    }

    def "verifyPublishProperties returns list with tmplOwner when not set"() {
        given: "A mock task without tmplOwner property"
        SimpleManifestEntry mockTask = Mock()

        expect:
        rule.verifyPublishProperties(mockTask).contains("tmplOwner")
    }

    def "publish task includes archive in manifest"() {
        given: "A complete setup"
        def templateDir = project.file("src/templates/my-template")
        templateDir.mkdirs()
        new File(templateDir, "DESCRIPTION").text = "Test template"
        project.extensions.lazybones.templateDirs.setFrom([templateDir])
        project.extensions.lazybones.templateOwner = "Test Owner"

        and: "A package task exists"
        def pkgTask = project.tasks.create("packageTemplateMyTemplate", Zip) {
            archiveBaseName.set("my-template")
            archiveVersion.set("1.0.0")
            destinationDirectory.set(project.buildDir)
        }

        when: "The rule is applied"
        rule.apply("publishTemplateMyTemplate")

        then: "The publish task is created and linked to package task"
        def publishTask = project.tasks.findByName("publishTemplateMyTemplate") as SimpleManifestEntry
        publishTask != null
        publishTask.artifactFile == pkgTask.archivePath
    }
}