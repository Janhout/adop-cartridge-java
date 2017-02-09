// Folders
def projectFolderName = "${PROJECT_NAME}"

// Variables
def slaveTemplateGitUrl = "ssh://jenkins@gerrit:29418/${PROJECT_NAME}/adop-cartridge-java-slave-template"

// Jobs
def slaveProvisioningPipelineView = buildPipelineView(projectFolderName + "/Slave_Provisioning")
def createSlaveJob = freeStyleJob(projectFolderName + "/Create_Slave")
def destroySlaveJob = freeStyleJob(projectFolderName + "/Destroy_Slave")
def listSlaveJob = freeStyleJob(projectFolderName + "/List_Slave")

// Create Slave
createSlaveJob.with{
    label("docker")
    environmentVariables {
        env('PROJECT_NAME',projectFolderName)
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    steps {
        shell('''set +x
                |function createDockerContainer() {
                |    export JAVA_SLAVE_NAME="$(echo ${PROJECT_NAME} | tr '/' '_')_slave"
                |    docker-compose -p ${JAVA_SLAVE_NAME} up -d
				|    docker cp /root/.docker/ ${JAVA_SLAVE_NAME}:/root/.docker/
                |}
				|createDockerContainer
				|set -x'''.stripMargin())
    }
    scm {
        git {
            remote {
                name("origin")
                url("${slaveTemplateGitUrl}")
                credentials("adop-jenkins-master")
            }
            branch("*/master")
        }
    }
    publishers {
        buildPipelineTrigger("${PROJECT_NAME}/Destroy_Slave") {
            parameters {
                currentBuild()
            }
        }
    }
}
queue(createSlaveJob)
// Destroy Slave
destroySlaveJob.with{
    description("This job deletes the slave.")
    label("docker")
    environmentVariables {
        env('PROJECT_NAME',projectFolderName)
    }
    scm {
        git {
            remote {
                name("origin")
                url("${slaveTemplateGitUrl}")
                credentials("adop-jenkins-master")
            }
            branch("*/master")
        }
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    steps {
        shell('''set +x
                |function deleteDockerContainer() {
                |	 export SLAVE_NAME="$(echo ${PROJECT_NAME} | tr '/' '_')_slave"
                |    docker-compose -p ${SLAVE_NAME} stop
                |    docker-compose -p ${SLAVE_NAME} rm -f
                |}
				|deleteDockerContainer
				|set -x'''.stripMargin())
    }
}

// Pipeline
slaveProvisioningPipelineView.with{
    title('Slave Provisioning Pipeline')
    displayedBuilds(5)
    selectedJob("Create_Slave")
    showPipelineParameters()
    showPipelineDefinitionHeader()
    refreshFrequency(5)
}


// List Slaves
listSlaveJob.with{
    label("docker")
    environmentVariables {
        env('PROJECT_NAME',projectFolderName)
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    steps {
        shell('''set +x
				|export JAVA_SLAVE_NAME="$(echo ${PROJECT_NAME} | tr '/' '_')_slave"
                |docker ps --filter status=running --filter "name=${JAVA_SLAVE_NAME}"
                |echo "=.=.=.=.=.=.=.=.=.=.=.=."
                |echo "=.=.=.=.=.=.=.=.=.=.=.=."
                |echo "List of running slaves -"
                |docker ps --filter status=running --filter "name=${JAVA_SLAVE_NAME}" --format "\t{{.Names}}"
                |echo "=.=.=.=.=.=.=.=.=.=.=.=."
                |echo "=.=.=.=.=.=.=.=.=.=.=.=."
                |set -x'''.stripMargin())
    }
}
