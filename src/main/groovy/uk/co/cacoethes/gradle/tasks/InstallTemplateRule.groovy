package uk.co.cacoethes.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.Rule
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Zip
import uk.co.cacoethes.gradle.util.NameConverter

/**
 * A rule that creates tasks for installing a Lazybones template package into the
 * template cache (typically ~/.skeletal/templates). The tasks have the name
 * 'installTemplate<templateName>', where the template name is in camel-case. The
 * install tasks are automatically configured to depend on the corresponding
 * package task defined by {@link PackageTemplateRule}.
 */
class InstallTemplateRule implements Rule {
    private static final String TEMPLATE_NAME_PATTERN = /installTemplate([A-Z\-]\S+)/

    Project project

    InstallTemplateRule(Project project) {
        this.project = project
    }

    @Override
    void apply(String taskName) {
        def templateName = extractTemplateName(taskName)
        if (templateName) {
            def packageTask = findPackageTask(templateName)
            if (packageTask) {
                createInstallTask(taskName, packageTask)
            }
        }
    }

    private String extractTemplateName(String taskName) {
        def matcher = taskName =~ TEMPLATE_NAME_PATTERN
        return matcher ? matcher[0][1] : null
    }

    private Zip findPackageTask(String templateName) {
        def taskName = "packageTemplate${templateName}"
        return project.tasks.findByName(taskName) as Zip
    }

    private void createInstallTask(String taskName, Zip packageTask) {
        def lazybonesExtension = project.extensions.lazybones
        project.tasks.create(taskName, Copy).with {
            from packageTask
            rename createPackageFileNameTransformer(lazybonesExtension.packageNameSuffix)
            into lazybonesExtension.installDir
        }
    }

    private Closure<String> createPackageFileNameTransformer(String packageSuffix) {
        return { String fileName ->
            fileName.replaceAll(/(.*)${packageSuffix}(\-[0-9].*)\.zip/, '$1$2.zip')
        }
    }

    @Override
    String getDescription() {
        return "installTemplate<TmplName> - Installs the named template package into your local cache"
    }

    @Override
    String toString() {
        return "Rule: $description"
    }
}
