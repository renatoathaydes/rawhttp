import groovy.transform.TupleConstructor
import org.gradle.api.Project

@TupleConstructor
class RawHttpGradle {

    Project project

    /**
     * Workaround function to find a property whether it is in the project itself,
     * in the local gradle.properties file,
     * or in the $GRADLE_USER_HOME/gradle.properties file.
     *
     * @param name of the property
     */
    def getGradleProperty(String name) {
        // this should return true in the first 2 cases: project property or defined in local gradle.properties
        project.hasProperty(name) ?
                project.property(name) :
                getUserHomeProperty(name)
    }

    private static getUserHomeProperty(String name) {
        def gradleHome = System.getenv('GRADLE_USER_HOME') ?: "${System.getProperty('user.home')}/gradle"
        if (gradleHome) {
            def gradleHomeProps = new File(gradleHome, 'gradle.properties')
            if (gradleHomeProps.isFile()) {
                Properties props = new Properties()
                props.load(new FileInputStream(gradleHomeProps))
                return props.getProperty(name)
            }
        }
        return null
    }

}