package uk.co.cacoethes.gradle.tasks

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir


class SimpleManifestEntrySpec extends Specification {
    @TempDir File tempDirPath
    @Shared String mockPath = "dummy/1.0/dummy-template-1.0.zip"
    @Shared File manifest

    def setup() {
        manifest = new File(tempDirPath, "skeletal-manifest.txt")
    }

    def "the manifest is written correctly"() {

        given: "an artifact to publish"
        def mockFile = GroovySpy(File, constructorArgs: ["/var/tmp/path/artifact.zip"]) {
            size() >> 1000
            newInputStream() >> new BufferedInputStream(new ByteArrayInputStream("test".getBytes("UTF-8")))
        }

        def project = ProjectBuilder.builder().build()
        def task = project.tasks.register("testPublish", SimpleManifestEntry) {
            artifactFile = mockFile
            packageName = "artifact"
            version = "1.0"
            tmplOwner = "My Owner"
            tmplDescription = "My awesome template."
            tmplDestination = tempDirPath.toString()
        }.get()

        when: "I publish the artifact"
        task.publish()

        then: "the manifest is correct"
        manifest.text == 'name,version,owner,description\nartifact,1.0,"My Owner","My awesome template."\n'
        
    }
}
