pipeline {
    agent any
    tools {
        jdk 'jdk-25'
    }
    environment {
        DOCKER_REGISTRY = credentials('docker-registry-url')
    }
    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script { env.GIT_SHA = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim() }
            }
        }
        stage('Build') {
            steps { sh './mvnw clean compile -pl catalog -am -DskipTests' }
        }
        stage('Unit Tests') {
            steps { sh './mvnw test -pl catalog -Dgroups=unit' }
            post { always { junit '**/surefire-reports/*.xml' } }
        }
        stage('Verify (integration + coverage gate)') {
            environment { TESTCONTAINERS_RYUK_DISABLED = 'true' }
            // Full verify: runs the whole suite (incl. Testcontainers integration tests) and
            // enforces jacoco:check (BUNDLE line >=80%, service package =100%). Fails here on a gap.
            steps { sh './mvnw verify -pl catalog -am' }
            post { always { junit '**/surefire-reports/*.xml' } }
        }
        stage('Publish Coverage') {
            // Reporting only — the gate already enforced above. Renders the JaCoCo trend in Jenkins.
            steps { jacoco(execPattern: '**/jacoco.exec') }
        }
        stage('Docker Build') {
            steps { sh "docker build -t ${env.DOCKER_REGISTRY}/catalog:${env.GIT_SHA} -f catalog/Dockerfile ." }
        }
        stage('Docker Push') {
            when { branch 'main' }
            steps {
                withCredentials([usernamePassword(credentialsId: 'docker-registry-creds',
                        usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    sh 'echo $DOCKER_PASS | docker login $DOCKER_REGISTRY -u $DOCKER_USER --password-stdin'
                    sh "docker push ${env.DOCKER_REGISTRY}/catalog:${env.GIT_SHA}"
                }
            }
        }
        stage('Deploy to Dev') {
            when { branch 'main' }
            steps {
                echo 'Deploy placeholder — configure kubectl set image or docker-compose up for target environment'
            }
        }
    }
    post {
        always { archiveArtifacts artifacts: '**/jacoco.exec', allowEmptyArchive: true }
        failure { echo 'Build failed — configure Slack/email notification webhook' }
        cleanup { sh "docker rmi ${env.DOCKER_REGISTRY}/catalog:${env.GIT_SHA} || true" }
    }
}
