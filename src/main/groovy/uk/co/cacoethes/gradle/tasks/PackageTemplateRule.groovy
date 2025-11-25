package uk.co.cacoethes.gradle.tasks

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.Rule
import org.gradle.api.Task
import org.gradle.api.tasks.bundling.Zip
import uk.co.cacoethes.gradle.lazybones.TemplateConvention
import uk.co.cacoethes.gradle.util.NameConverter

/**
 * <p>A rule that creates tasks for packaging a Lazybones template as a zip file.
 * The tasks have the name 'packageTemplate<templateName>', where the template
 * name should be camel-case. The template name is then converted to hyphenated,
 * lower-case form to determine the directory containing the template source.</p>
 * <p>Each task is of type Zip and can be minimally configured through the {@link
 * uk.co.cacoethes.gradle.lazybones.LazybonesConventions Lazybones conventions}.
 * You can control the name suffix used for the name of the zip package (via
 * {@code packageNameSuffix} and where the packages are created (via {@code
 * packagesDir}. Also, the tasks automatically exclude .retain and VERSION files
 * from the generated zip file, as well as any .gradle directory in the root.</p>
 */
class PackageTemplateRule implements Rule {
    private static final String TASK_NAME_PATTERN = /packageTemplate([A-Z\-]\S+)/

    Project project

    PackageTemplateRule(Project project) {
        this.project = project
    }

    @Override
    void apply(String taskName) {
        def matcher = taskName =~ TASK_NAME_PATTERN
        if (!matcher) return

        def templateName = taskToTemplateName(matcher[0][1])
        def templateDir = findTemplateDirectory(templateName)

        if (!isValidTemplateDirectory(templateDir)) return

        def templateConvention = findTemplateConvention(templateName)
        def task = createTask(taskName, templateName, templateDir, templateConvention)
        addSubTemplatesToPackageTask(templateConvention, task)
    }

    protected File findTemplateDirectory(String templateName) {
        return project.extensions.lazybones.templateDirs.files.find { f -> f.name == templateName }
    }

    protected boolean isValidTemplateDirectory(File templateDir) {
        return templateDir != null && templateDir.exists()
    }

    protected TemplateConvention findTemplateConvention(String tmplName) {
        return project.extensions.lazybones.templateConventions.find { it.name == tmplName }
    }

    protected void addSubTemplatesToPackageTask(TemplateConvention tmplConvention, Task task) {
        if (!tmplConvention?.includes) return

        def subPackageTasks = collectSubPackageTasks(tmplConvention.includes)
        configureSubPackageDependencies(task, subPackageTasks)
    }

    protected List<Task> collectSubPackageTasks(List<String> subTemplateNames) {
        return subTemplateNames.collect { String subpkgName ->
            project.tasks.getByName(subPackageTaskName(subpkgName))
        }
    }

    protected void configureSubPackageDependencies(Task task, List<Task> subPackageTasks) {
        task.dependsOn(subPackageTasks)
        task.from(subPackageTasks*.archivePath) {
            into(".lazybones")
            rename(/^subtmpl-(.*\.zip)/, '$1')
        }
    }

    protected Task createTask(String taskName, String tmplName, File tmplDir, TemplateConvention tmplConvention) {
        validateTemplateVersion(tmplConvention, tmplDir, tmplName)

        def packageConfig = createPackageConfiguration(tmplConvention)
        def version = resolveTemplateVersion(tmplConvention, tmplDir)

        Zip task = createZipTask(taskName, tmplName, version)
        configureTaskFileInclusion(task, tmplDir, packageConfig)
        addValidationToTask(task, tmplDir, tmplName)

        return task
    }

    protected Zip createZipTask(String taskName, String tmplName, String version) {
        return project.tasks.create(taskName, Zip) {
            archiveBaseName.set("${tmplName}${project.extensions.lazybones.packageNameSuffix}")
            destinationDirectory.set(project.extensions.lazybones.packagesDir)
            archiveVersion.set(version)
            includeEmptyDirs = true
        }
    }

    protected String resolveTemplateVersion(TemplateConvention tmplConvention, File tmplDir) {
        return tmplConvention?.version ?: project.file("$tmplDir/VERSION").text.trim()
    }

    protected void configureTaskFileInclusion(Zip task, File tmplDir, Map<String, Object> packageConfig) {
        configureDefaultFileInclusion(task, tmplDir, packageConfig)
        configureFilesWithSpecificModes(task, tmplDir, packageConfig)
    }

    protected void addValidationToTask(Zip task, File tmplDir, String tmplName) {
        task.doFirst {
            validateTemplateDir(tmplDir, tmplName)
        }
    }

    protected Map<String, Object> createPackageConfiguration(TemplateConvention tmplConvention) {
        return [
                packageExcludes: tmplConvention?.packageExcludes ?: project.extensions.lazybones.packageExcludes,
                fileModes      : tmplConvention?.fileModes ?: project.extensions.lazybones.fileModes
        ]
    }

    protected void configureDefaultFileInclusion(Zip task, File tmplDir, Map<String, Object> packageConfig) {
        task.from tmplDir, {
            if (packageConfig.fileModes) {
                packageConfig.fileModes.each { String mode, List<String> patterns ->
                    exclude patterns
                }
            }
            if (packageConfig.packageExcludes) {
                exclude packageConfig.packageExcludes
            }
        }
    }

    protected void configureFilesWithSpecificModes(Zip task, File tmplDir, Map<String, Object> packageConfig) {
        if (!packageConfig.fileModes) return

        packageConfig.fileModes.each { String mode, List<String> patterns ->
            addFilesWithMode(task, tmplDir, mode, patterns, packageConfig.packageExcludes)
        }
    }

    protected void addFilesWithMode(Zip task, File tmplDir, String mode, List<String> patterns, List<String> packageExcludes) {
        task.from tmplDir, {
            include patterns
            if (packageExcludes) {
                exclude packageExcludes
            }
            fileMode = unixModeStringToInteger(mode)
            dirMode = unixModeStringToInteger(mode)
        }
    }

    protected void validateTemplateVersion(TemplateConvention tmplConvention, File tmplDir, String tmplName) {
        if (hasVersionInfo(tmplConvention, tmplDir)) return

        throw new InvalidUserDataException("Project template '${tmplName}' has no source of version info")
    }

    protected boolean hasVersionInfo(TemplateConvention tmplConvention, File tmplDir) {
        return tmplConvention?.version || (tmplDir != null && new File(tmplDir, "VERSION").exists())
    }

    protected String taskToTemplateName(String requestedTemplateName) {
        return requestedTemplateName.startsWith("-") ?
                requestedTemplateName.substring(1) :
                NameConverter.camelCaseToHyphenated(requestedTemplateName)
    }

    protected String subPackageTaskName(String subpkgName) {
        return "packageTemplateSubtmpl${NameConverter.hyphenatedToCamelCase(subpkgName)}".toString()
    }

    protected void validateTemplateDir(File tmplDir, String tmplName) {
        if (!tmplDir?.exists()) {
            throw new InvalidUserDataException("No project template directory found for '${tmplName}'")
        }
    }

    protected int unixModeStringToInteger(String mode) {
        return Integer.valueOf(mode, 8)
    }

    @Override
    String getDescription() {
        return "packageTemplate<TmplName> - Packages the template in the directory matching the task name"
    }

    @Override
    String toString() {
        return "Rule: $description"
    }
}
