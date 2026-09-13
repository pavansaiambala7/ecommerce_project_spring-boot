// Replaces the empty, misnamed "jenkins file" (Jenkins looks for "Jenkinsfile").
pipeline {
    agent any

    environment {
        SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:5432/ecommjava'
        SPRING_DATASOURCE_USERNAME = 'postgres'
        SPRING_DATASOURCE_PASSWORD = 'postgres'
        GEMINI_API_KEY = credentials('gemini-api-key')
        JWT_SECRET = credentials('jwt-secret')
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh './mvnw -B clean compile'
            }
        }

        stage('Test') {
            steps {
                sh './mvnw -B verify'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                    archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true
                }
            }
        }

        stage('Package') {
            steps {
                sh './mvnw -B package -DskipTests'
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
        }
    }

    post {
        cleanup {
            cleanWs()
        }
    }
}
