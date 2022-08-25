# Skeletal Gradle Plugin

The mechanics of publishing Lazybones templates is straightforward and could
be done manually. That doesn't mean it's not a lot of work though. If you want
to manage and publish Lazybones templates, we strongly recommend you use Gradle
along with this plugin.

The plugin allows you to manage multiple templates, giving you the tools to
package, install, and publish them individually or all together. In addition, 
you can also easily set up subtemplates. Let's see how you use the plugin.

## Installation

The plugin is available in the Gradle [plugin portal](https://plugins.gradle.org/plugin/net.codebuilders.lazybones-templates).
To configure it using the plugins DSL:

    plugins {
        id "net.codebuilders.lazybones-templates" version "1.5.0"
    }
    ...
or using the legacy plugin application:

    buildscript {
        repositories {
            maven {
                url "https://plugins.gradle.org/m2/"
            }
        }
        dependencies {
            classpath "net.codebuilders:skeletal-gradle:1.5.0"
        }
    }

    apply plugin: "net.codebuilders.lazybones-templates"
	...

## Conventions & Required Configuration

The Skeletal plugin relies on a whole set of conventions so that you can get
started as quickly and painlessly as possible. The basic directory structure
that the plugin relies on looks like this:

    <root>
      ├── build.gradle
      └── templates/
          ├── mytmpl/
          │
          .
          .
          
Each directory under 'templates' contains the source files for one project
template. The name of the project template derives from the name of the
directory. In the above example, we end up with a project template named
'mytmpl'. For more information on what goes inside these project template
directories, see the [Template Developers Guide](https://github.com/cbmarcum/skeletal/wiki/Template-Developers-Guide).

Lazybones required `repositoryName`, `repositoryUsername`, and `repositoryApiKey` 
properties need for Bintray publishing to be in the `lazybones` configuration 
block. These are deprecated in Skeletal.

    lazybones {
    	repositoryName = "<user>/<repo>"      // Bintray repository
    	repositoryUsername = "dilbert"
    	repositoryApiKey = "DFHWIRUEFHIWEJFNKWEJBFLWEFEFHLKDFHSLKDF"
    }

Likewise, the older `repositoryUrl` property is not currently used by Skeletal 
either.

## Tasks

The plugin adds 3 rules and 3 concrete tasks to your project. The 3 rules are:

* `packageTemplate<TmplName>` - Packages the named project template directory
as a zip file.
  
* `installTemplate<TmplName>` - Copies the template package (the zip file) into
your local Skeletal cache.

* `publishTemplate<TmplName>` - Packages named template and creates or updates 
the `skeletal-manifest.txt` file in the build ouput alongside the packages for 
moving to your simple URL repository so that other people can use it.

The template name is derived from the corresponding directory name. The plugin
assumes that the directory name is in lower-case hyphenated form (such as
my-proj-template) and turns that into camel case for the template name (e.g.
MyProjTemplate). So, to package and install one of your project templates, you
just execute

    ./gradlew installTemplateMyProjTemplate
    
Each of these rules has a corresponding task that applies the rule to every
template in your project:

* `packageAllTemplates`
* `installAllTemplates`
* `publishAllTemplates`

As long as you stick to the conventions, that's all you need.
 
## Managing Subtemplates

Template authors can create subtemplates inside their project templates. These 
allow users to perform extra code generation in a project after it has been 
created from a Lazybones project template.

There are basically two steps to setup subtemplates:

1. Add the subtemplates as directories alongside the project templates, giving
   each directory a `subtmpl-` prefix to its name.

2. Add a directive to the `lazybones` configuration block telling the plugin
   which subtemplates are to be packaged in which project templates.

The first of these will result in a project structure like this:

    <root>
      ├── build.gradle
      └── templates/
          ├── grails-standard/
          ├── subtmpl-controller/
          ├── subtmpl-domain-class/
          .
          .

The `subtmpl-` prefix ensures that the plugin won't attempt to publish the
subtemplates, since they should not exist independently of a project template.

Once you have created the subtemplate directories and populated them with
files and a post-install script, you need to link them to project templates.
To do that, just add this setting:

    lazybones {
        ...
        template "grails-standard" includes "controller", "domain-class"
	}

This states that the 'grails-standard' project template should include the
'subtmpl-controller' and 'subtmpl-domain-class' subtemplates. Note that you
don't need to include the `subtmpl-` prefix in the configuration setting. It's
implied.

Now when you package the 'grails-standard' project template, it will
automatically include the 'subtmpl-controller' and 'subtmpl-domain-class'
packages also.

## Advanced configuration

Even though the Skeletal Gradle plugin makes use of conventions, you can still
override most of them by setting properties in the `lazybones` configuration
block. Here is a selection of them:

* `templateDirs` - set to a `FileCollection` containing the locations of the
project template directories.

* `packagesDir` - a `File` representing the location where the template package
files are created.

* `installDir` - a `File` representing the location where template packages are
installed to.

Since Skeletal publishing uses a manifest file and doesn't upload to
Bintray like Lazybones did, these properties are not currently used but left in 
case they are needed for customized builds.

* `licenses` - a list of license names, such as "Apache-2.0".

* `publish` - a boolean.

The full set of options are defined on the [LazybonesConventions](https://github.com/cbmarcum/skeletal/blob/master/lazybones-gradle-plugin/src/main/groovy/uk/co/cacoethes/gradle/lazybones/LazybonesConventions.groovy)
class.

For more advanced use cases, you can configure the plugin's tasks directly. The
package tasks are instances of the standard Gradle `Zip` task, while the install
tasks are instances of the standard `Copy`.

Currently, package publishing is done through the [SimpleManifestEntry](https://github.com/cbmarcum/skeletal/blob/master/lazybones-gradle-plugin/src/main/groovy/uk/co/cacoethes/gradle/tasks/SimpleManifestEntry.groovy),
which packages one or all plugins and creates or updates a `skeletal-manifest.txt` 
file for copying to your simple URL repository.
