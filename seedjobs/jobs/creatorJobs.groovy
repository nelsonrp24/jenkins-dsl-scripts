seedjobs = [
    [ name: 'testing1', external: ['testing1/jobs/*.groovy'] ]
    //[ name: 'testing1', external: ['testing1/folders/*.groovy', 'testing1/jobs/*.groovy'] ],
]

seedjobs.each { seedjob ->
  def jobName = 'seedjob-' + seedjob.name

  job(jobName) {
    scm {
      git {
        remote {
          url('https://github.com/nelsonrp24/jenkins-dsl-scripts.git')
        }
        branch('master')
        extensions {
          cleanBeforeCheckout()
          cloneOptions {
            shallow(true)
          }
        }
      }
    }
    wrappers {
      timestamps()
      colorizeOutput('xterm')
      buildUserVars()
    }
    steps {
      dsl{
        external(seedjob.external)
        additionalClasspath('dsl-helpers')
      }
    }
  }

  queue(jobName)
}
