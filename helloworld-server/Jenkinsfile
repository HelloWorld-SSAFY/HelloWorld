pipeline {
  agent any
  options { timestamps() }
  stages {
    stage('Sanity') {
      steps {
        sh 'echo "Triggered by webhook at $(date)"'
        sh 'kubectl get nodes || true'
        sh 'docker version --format "{{.Server.Version}}" || true'
      }
    }
  }
}
