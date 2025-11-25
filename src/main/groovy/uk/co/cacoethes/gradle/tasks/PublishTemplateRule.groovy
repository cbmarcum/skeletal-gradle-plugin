package uk.co.cacoethes.gradle.tasks

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Rule
import org.gradle.api.tasks.bundling.Zip

import uk.co.cacoethes.gradle.lazybones.LazybonesConventions
import uk.co.cacoethes.gradle.util.NameConverter

/**
 * <p>A rule that creates tasks for publishing a Lazybones template package for a
 * Simple URL repository. The tasks have the name 'publishTemplate<templateName>',
 * where the template name is in camel-case. The publish tasks are automatically
 * configured to depend on the corresponding package task defined by
 * {@link PackageTemplateRule}.</p>
 * <p>The tasks will create a manifest file along with the template package.</p>
 */
class PublishTemplateRule implements Rule {
    private static final String TASK_NAME_PATTERN = /publishTemplate([A-Z\-]\S+)/

    Project project

    PublishTemplateRule(Project project) {
        this.project = project
    }

    @Override
    void apply(String taskName) {
        def matcher = taskName =~ TASK_NAME_PATTERN
        if (matcher) {
            def camelCaseTemplateName = matcher[0][1]
            def templateName = taskToTemplateName(camelCaseTemplateName)
            def packageTask = findPackageTask(camelCaseTemplateName)

            if (!packageTask) return

            def templateDirectory = findTemplateDirectory(templateName)
            def templateDescription = extractTemplateDescription(templateDirectory)

            createPublishTask(taskName, packageTask, templateName, templateDescription)
        }
    }

    private Zip findPackageTask(String camelCaseTemplateName) {
        return (Zip) project.tasks.findByName("packageTemplate${camelCaseTemplateName}")
    }

    private File findTemplateDirectory(String templateName) {
        return project.extensions.lazybones.templateDirs.files.find { f -> f.name == templateName }
    }

    private String extractTemplateDescription(File templateDirectory) {
        def descriptionFile = new File("$templateDirectory/DESCRIPTION")
        return descriptionFile.exists() ?
                project.file("$templateDirectory/DESCRIPTION").text.trim() :
                "Missing DESCRIPTION file"
    }

    private void createPublishTask(String taskName, Zip packageTask, String templateName, String templateDescription) {
        def lazybonesExtension = project.extensions.lazybones

        project.tasks.create(taskName, SimpleManifestEntry) { task ->
            dependsOn packageTask
            artifactFile = packageTask.archivePath
            packageName = packageTask.archiveBaseName.get()
            version = packageTask.archiveVersion.get()
            tmplOwner = lazybonesExtension.templateOwner
            tmplDescription = templateDescription
            tmplDestination = packageTask.destinationDirectory.get()

            doFirst {
                validatePublishConfiguration(task, packageTask)
            }
        }
    }

    private void validatePublishConfiguration(task, Zip packageTask) {
        def missingProperties = verifyPublishProperties(task)

        if (!task.tmplOwner && !task.tmplDescription) {
            missingProperties << "repositoryName"
        }

        if (missingProperties) {
            throw new GradleException(buildMissingPropertiesMessage(missingProperties))
        }

        if (!task.artifactFile.exists()) {
            throw new GradleException(
                    "Bad build file: zip archive '${packageTask.archiveName}' does not exist, " +
                            "but should have been created automatically.")
        }
    }

    private String buildMissingPropertiesMessage(List missingProperties) {
        return """\
You must provide values for these settings:
    ${missingProperties.join(", ")}
For example, in your build file:
    lazybones {
        templateOwner = "Your Name"
    }
"""
    }

    protected String taskToTemplateName(String requestedTemplateName) {
        // The rule supports tasks of the form packageTemplateMyTmpl and
        // packageTemplate-my-tmpl. Only the former requires conversion of
        // the name to lowercase hyphenated.
        return requestedTemplateName.startsWith("-") ?
                requestedTemplateName.substring(1) :
                NameConverter.camelCaseToHyphenated(requestedTemplateName)
    }

    /**
     * @return a list of convention properties that are required for publishing
     * and need supplied in the build file.
     */
    protected List verifyPublishProperties(task) {
        ["tmplOwner"].findAll { !task.getProperty(it) }
    }

    @Override
    String getDescription() {
        return "publishTemplate<TmplName> - Publishes the named template package for a URL repository"
    }

    @Override
    String toString() {
        return "Rule: $description"
    }
}
