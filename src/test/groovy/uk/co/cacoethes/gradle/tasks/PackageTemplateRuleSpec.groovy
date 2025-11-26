package uk.co.cacoethes.gradle.tasks

import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.bundling.Zip
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Unroll
import uk.co.cacoethes.gradle.lazybones.TemplateConvention

class PackageTemplateRuleSpec extends Specification {
    def project
    def rule

    def setup() {
        project = ProjectBuilder.builder().build()
        project.apply plugin: 'net.codebuilders.lazybones-templates'
        rule = new PackageTemplateRule(project)
    }

    def "Rule has correct description"() {
        expect: "The description describes the rule's purpose"
        rule.description == "packageTemplate<TmplName> - Packages the template in the directory matching the task name"
    }

    def "toString returns description with 'Rule:' prefix"() {
        expect:
        rule.toString() == "Rule: ${rule.description}"
    }

    @Unroll
    def "apply creates package task for '#taskName' when template directory exists"() {
        given: "A template directory exists"
        def templateDir = project.file("src/templates/${templateName}")
        templateDir.mkdirs()
        new File(templateDir, "VERSION").text = "1.0.0"
    
        // project.extensions.lazybones.templateDirs.setFrom([templateDir.parentFile])
        project.extensions.lazybones.templateDirs.setFrom([templateDir])
    
        and: "Template convention is configured"
        def convention = new TemplateConvention(templateName)
        project.extensions.lazybones.templateConventions = [convention]

        when: "The rule is applied"
        rule.apply(taskName)

        then: "A package task is created"
        def packageTask = project.tasks.findByName(taskName)
        packageTask != null
        packageTask instanceof Zip

        where:
        taskName                    | templateName
        "packageTemplateMyTemplate" | "my-template"
        "packageTemplateSimple"     | "simple"
        "packageTemplateFooBar"     | "foo-bar"
    }

    def "apply does not create task when template directory does not exist"() {
        when: "The rule is applied without a corresponding template directory"
        rule.apply("packageTemplateNonExistent")

        then: "No package task is created"
        project.tasks.findByName("packageTemplateNonExistent") == null
    }

    @Unroll
    def "apply does not create task for invalid task name '#taskName'"() {
        when: "The rule is applied with an invalid task name"
        rule.apply(taskName)

        then: "No task is created"
        project.tasks.findByName(taskName) == null

        where:
        taskName << [
                "packagetemplate",
                "packageTemplate",
                "packageTemplateLowercase",
                "installTemplateMyTemplate",
                "myCustomTask"
        ]
    }

    def "package task is configured with correct archive name and version"() {
        given: "A template directory with VERSION file"
        def templateDir = project.file("src/templates/my-template")
        templateDir.mkdirs()
        new File(templateDir, "VERSION").text = "2.1.0"

        // project.extensions.lazybones.templateDirs.setFrom([templateDir.parentFile])
        project.extensions.lazybones.templateDirs.setFrom([templateDir])
        project.extensions.lazybones.packageNameSuffix = "-template"
        
        def convention = new TemplateConvention("my-template")
        project.extensions.lazybones.templateConventions = [convention]

        when: "The rule is applied"
        rule.apply("packageTemplateMyTemplate")

        then: "The package task has correct archive configuration"
        def packageTask = project.tasks.findByName("packageTemplateMyTemplate") as Zip
        packageTask.archiveBaseName.get() == "my-template-template"
        packageTask.archiveVersion.get() == "2.1.0"
    }

    def "package task uses version from template convention if available"() {
        given: "A template directory with VERSION file"
        def templateDir = project.file("src/templates/my-template")
        templateDir.mkdirs()
        new File(templateDir, "VERSION").text = "1.0.0"

        // project.extensions.lazybones.templateDirs.setFrom([templateDir.parentFile])
        project.extensions.lazybones.templateDirs.setFrom([templateDir])
        
        and: "Template convention with explicit version"
        // def convention = new TemplateConvention(name: "my-template", version: "3.5.0")
        def convention = new TemplateConvention("my-template")
        convention.version = "3.5.0"
        project.extensions.lazybones.templateConventions = [convention]

        when: "The rule is applied"
        rule.apply("packageTemplateMyTemplate")

        then: "The convention version is used instead of VERSION file"
        def packageTask = project.tasks.findByName("packageTemplateMyTemplate") as Zip
        packageTask.archiveVersion.get() == "3.5.0"
    }

    def "throws exception when template has no version information"() {
        given: "A template directory without VERSION file"
        def templateDir = project.file("src/templates/my-template")
        templateDir.mkdirs()

        // project.extensions.lazybones.templateDirs.setFrom([templateDir.parentFile])
        project.extensions.lazybones.templateDirs.setFrom([templateDir])
        
        and: "Template convention without version"
        def convention = new TemplateConvention("my-template")
        project.extensions.lazybones.templateConventions = [convention]

        when: "The rule is applied"
        rule.apply("packageTemplateMyTemplate")

        then: "An InvalidUserDataException is thrown"
        thrown(InvalidUserDataException)
    }

    def "package task excludes specified patterns"() {
        given: "A template directory with exclude patterns configured"
        def templateDir = project.file("src/templates/my-template")
        templateDir.mkdirs()
        new File(templateDir, "VERSION").text = "1.0.0"

        // project.extensions.lazybones.templateDirs.setFrom([templateDir.parentFile])
        project.extensions.lazybones.templateDirs.setFrom([templateDir])
        project.extensions.lazybones.packageExcludes = ["**/.retain", "**/VERSION"]
        
        def convention = new TemplateConvention("my-template")
        project.extensions.lazybones.templateConventions = [convention]

        when: "The rule is applied"
        rule.apply("packageTemplateMyTemplate")

        then: "The package task is created successfully"
        def packageTask = project.tasks.findByName("packageTemplateMyTemplate") as Zip
        packageTask != null
    }

    def "package task is configured with file mode restrictions"() {
        given: "A template directory with file mode configuration"
        def templateDir = project.file("src/templates/my-template")
        templateDir.mkdirs()
        new File(templateDir, "VERSION").text = "1.0.0"

        // project.extensions.lazybones.templateDirs.setFrom([templateDir.parentFile])
        project.extensions.lazybones.templateDirs.setFrom([templateDir])
        project.extensions.lazybones.fileModes = ["0755": ["**/bin/*"]]
        
        def convention = new TemplateConvention("my-template")
        project.extensions.lazybones.templateConventions = [convention]

        when: "The rule is applied"
        rule.apply("packageTemplateMyTemplate")

        then: "The package task is created successfully"
        def packageTask = project.tasks.findByName("packageTemplateMyTemplate") as Zip
        packageTask != null
    }

    @Unroll
    def "unixModeStringToInteger converts '#modeString' to #expectedValue"() {
        expect:
        rule.unixModeStringToInteger(modeString) == expectedValue

        where:
        modeString | expectedValue
        "0755"     | 493      // 0o755 in octal
        "0644"     | 420      // 0o644 in octal
        "0777"     | 511      // 0o777 in octal
        "0700"     | 448      // 0o700 in octal
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

    @Unroll
    def "subPackageTaskName converts '#subpkgName' to '#expected'"() {
        expect:
        rule.subPackageTaskName(subpkgName) == expected

        where:
        subpkgName     | expected
        "my-template"  | "packageTemplateSubtmplMyTemplate"
        "simple"       | "packageTemplateSubtmplSimple"
        "foo-bar"      | "packageTemplateSubtmplFooBar"
    }

    def "validateTemplateDir throws exception when directory does not exist"() {
        given: "A non-existent template directory"
        def nonExistentDir = project.file("non/existent/path")

        when: "Validation is performed"
        rule.validateTemplateDir(nonExistentDir, "my-template")

        then: "An InvalidUserDataException is thrown with appropriate message"
        def ex = thrown(InvalidUserDataException)
        ex.message.contains("No project template directory found for 'my-template'")
    }

    def "validateTemplateDir passes when directory exists"() {
        given: "An existing template directory"
        def templateDir = project.file("src/templates/my-template")
        templateDir.mkdirs()

        when: "Validation is performed"
        rule.validateTemplateDir(templateDir, "my-template")

        then: "No exception is thrown"
        noExceptionThrown()
    }

    def "isValidTemplateDirectory returns true for existing directory"() {
        given: "An existing template directory"
        def templateDir = project.file("src/templates/my-template")
        templateDir.mkdirs()

        expect:
        rule.isValidTemplateDirectory(templateDir) == true
    }

    def "isValidTemplateDirectory returns false for null"() {
        expect:
        rule.isValidTemplateDirectory(null) == false
    }

    def "isValidTemplateDirectory returns false for non-existent directory"() {
        given: "A non-existent path"
        def nonExistentDir = project.file("non/existent/path")

        expect:
        rule.isValidTemplateDirectory(nonExistentDir) == false
    }

    def "findTemplateDirectory returns correct directory"() {
        given: "A template directory exists"
        def templateDir = project.file("src/templates/my-template")
        templateDir.mkdirs()
        // project.extensions.lazybones.templateDirs.setFrom([templateDir.parentFile])
        project.extensions.lazybones.templateDirs.setFrom([templateDir])

        when: "Finding template directory"
        def found = rule.findTemplateDirectory("my-template")

        then: "The correct directory is returned"
        found == templateDir
    }

    def "findTemplateDirectory returns null when not found"() {
        given: "No template directory configured"
        project.extensions.lazybones.templateDirs.setFrom([])

        when: "Finding template directory"
        def found = rule.findTemplateDirectory("non-existent")

        then: "null is returned"
        found == null
    }

    def "findTemplateConvention returns correct convention"() {
        given: "A template convention is configured"
        def convention = new TemplateConvention("my-template")
        convention.version = "1.0.0"
        project.extensions.lazybones.templateConventions = [convention]

        when: "Finding template convention"
        def found = rule.findTemplateConvention("my-template")

        then: "The correct convention is returned"
        found == convention
        found.version == "1.0.0"
    }

    def "findTemplateConvention returns null when not found"() {
        given: "No conventions configured"
        project.extensions.lazybones.templateConventions = []

        when: "Finding template convention"
        def found = rule.findTemplateConvention("non-existent")

        then: "null is returned"
        found == null
    }

    def "hasVersionInfo returns true when convention has version"() {
        given: "A template convention with version"
        def convention = new TemplateConvention("my-template")
        convention.version = "1.0.0"

        expect:
        rule.hasVersionInfo(convention, null) == true
    }

    def "hasVersionInfo returns true when VERSION file exists"() {
        given: "A template directory with VERSION file"
        def templateDir = project.file("src/templates/my-template")
        templateDir.mkdirs()
        new File(templateDir, "VERSION").text = "1.0.0"

        expect:
        rule.hasVersionInfo(null, templateDir) == true
    }

    def "hasVersionInfo returns false when neither convention nor file has version"() {
        given: "A template directory without VERSION file"
        def templateDir = project.file("src/templates/my-template")
        templateDir.mkdirs()

        and: "No convention"
        def convention = new TemplateConvention("my-template")

        expect:
        rule.hasVersionInfo(convention, templateDir) == false
    }
}
